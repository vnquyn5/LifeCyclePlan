package com.lifestyleplanner.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifestyleplanner.auth.dto.response.TokenResponse;
import com.lifestyleplanner.auth.session.application.AuthSessionService;
import com.lifestyleplanner.auth.session.domain.entity.AuthRefreshToken;
import com.lifestyleplanner.auth.session.repository.AuthRefreshTokenRepository;
import com.lifestyleplanner.user.domain.entity.User;
import com.lifestyleplanner.user.repository.UserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Testcontainers
class AuthSessionPostgresIntegrationTest {
    private static final String PASSWORD = "Synthetic-Integration-Password-2026!";
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));
    private static final Path PRIVATE_KEY = Path.of(".local-secrets/jwt-private.pem").toAbsolutePath().normalize();
    private static final Path PUBLIC_KEY = Path.of(".local-secrets/jwt-public.pem").toAbsolutePath().normalize();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository users;

    @Autowired
    private AuthSessionService sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private AuthRefreshTokenRepository refreshTokens;

    @DynamicPropertySource
    static void registerTestConfiguration(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_PRIVATE_KEY_PATH", PRIVATE_KEY::toString);
        registry.add("JWT_PUBLIC_KEY_PATH", PUBLIC_KEY::toString);
        registry.add("JWT_ISSUER", () -> "lifecycleplan-auth02-integration-test");
        registry.add("app.security.auth-rate-limit.refresh-max-requests", () -> 4);
    }

    @Test
    void loginAndRefreshUsePostgresRotationThenReplayRevokesTheSessionFamily() throws Exception {
        User user = createUser(false);
        String correlationId = "auth02-refresh-it-1";
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Correlation-Id", correlationId)
                        .with(request -> { request.setRemoteAddr("192.0.2.11"); return request; })
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginPayload(user.getEmail(), PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        JsonNode loginTokens = loginBody.path("tokens");
        String accessToken = loginTokens.path("accessToken").asText();
        String oldRefreshToken = loginTokens.path("refreshToken").asText();
        assertFalse(accessToken.isBlank());
        assertFalse(oldRefreshToken.isBlank());
        assertEquals("Bearer", loginTokens.path("tokenType").asText());
        assertEquals(900, loginTokens.path("expiresIn").asLong());
        assertEquals(correlationId, login.getResponse().getHeader("X-Correlation-Id"));
        assertTrue(login.getResponse().getHeader("Cache-Control").contains("no-store"));
        assertEquals(user.getId().toString(), mockMvc.perform(get("/api/v1/test/protected-user")
                                .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        MvcResult refresh = postRefresh(oldRefreshToken, "192.0.2.11").andExpect(status().isOk()).andReturn();
        JsonNode refreshTokens = objectMapper.readTree(refresh.getResponse().getContentAsString()).path("tokens");
        String rotatedRefreshToken = refreshTokens.path("refreshToken").asText();
        assertFalse(rotatedRefreshToken.isBlank());
        assertNotEquals(oldRefreshToken, rotatedRefreshToken);
        assertEquals("Bearer", refreshTokens.path("tokenType").asText());
        assertEquals(900, refreshTokens.path("expiresIn").asLong());
        assertTrue(refresh.getResponse().getHeader("Cache-Control").contains("no-store"));

        assertInvalidRefresh(postRefresh(oldRefreshToken, "192.0.2.11"));
        assertInvalidRefresh(postRefresh(rotatedRefreshToken, "192.0.2.11"));
        assertEquals(1, countSessions(user.getId()));
    }

    @Test
    void unknownAndDisabledUsersCannotCreateLoginSessions() throws Exception {
        User disabled = createUser(true);
        String wrongPasswordBody = objectMapper.writeValueAsString(
                new LoginPayload(disabled.getEmail(), "wrong-password"));
        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("192.0.2.21"); return request; })
                        .contentType("application/json").content(wrongPasswordBody))
                .andExpect(status().isUnauthorized()).andReturn();
        JsonNode wrongPasswordError = objectMapper.readTree(wrongPassword.getResponse().getContentAsString());
        assertEquals("AUTH_INVALID_CREDENTIALS", wrongPasswordError.path("code").asText());

        MvcResult unknown = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("192.0.2.22"); return request; })
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginPayload(
                                "unknown-" + UUID.randomUUID() + "@example.test", PASSWORD))))
                .andExpect(status().isUnauthorized()).andReturn();
        assertEquals("AUTH_INVALID_CREDENTIALS", objectMapper.readTree(
                unknown.getResponse().getContentAsString()).path("code").asText());

        MvcResult disabledLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("192.0.2.23"); return request; })
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginPayload(
                                disabled.getEmail(), PASSWORD))))
                .andExpect(status().isForbidden()).andReturn();
        assertEquals("AUTH_ACCOUNT_DISABLED", objectMapper.readTree(
                disabledLogin.getResponse().getContentAsString()).path("code").asText());
        assertEquals(0, countSessions(disabled.getId()));
    }

    @Test
    void refreshPersistenceFailureRollsBackTheSessionAndReturnsASafeServerError() throws Exception {
        User user = createUser(false);
        try {
            doThrow(new DataIntegrityViolationException("synthetic persistence failure"))
                    .when(refreshTokens).saveAndFlush(any(AuthRefreshToken.class));

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> { request.setRemoteAddr("192.0.2.24"); return request; })
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new LoginPayload(user.getEmail(), PASSWORD))))
                    .andExpect(status().isInternalServerError());

            assertEquals(0, countSessions(user.getId()));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM auth_refresh_tokens WHERE user_id = ?", Long.class, user.getId()));
        } finally {
            reset(refreshTokens);
        }
    }

    @Test
    void refreshValidationUnknownExpiredAndRevokedCredentialsReturnExpectedStatuses() throws Exception {
        postRefresh("  ", "192.0.2.31").andExpect(status().isBadRequest());
        assertInvalidRefresh(postRefresh("unknown-refresh-token", "192.0.2.31"));

        User user = createUser(false);
        TokenResponse expired = sessions.issue(user);
        jdbc.update("UPDATE auth_refresh_tokens SET created_at = ?, expires_at = ? WHERE token_hash = ?",
                java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2020-01-08T00:00:00Z")),
                hash(expired.refreshToken()));
        assertInvalidRefresh(postRefresh(expired.refreshToken(), "192.0.2.31"));

        TokenResponse revoked = sessions.issue(user);
        jdbc.update("UPDATE auth_refresh_tokens SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ?",
                hash(revoked.refreshToken()));
        assertInvalidRefresh(postRefresh(revoked.refreshToken(), "192.0.2.31"));
    }

    @Test
    void concurrentRefreshCallsSerializeAndAReplayRevokesTheWinnerToken() throws Exception {
        User user = createUser(false);
        TokenResponse original = sessions.issue(user);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<Optional<TokenResponse>>> requests = Stream.generate(() -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timeout");
                return sessions.refresh(original.refreshToken());
            })).limit(2).toList();

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Optional<TokenResponse>> results = List.of(
                    requests.get(0).get(10, TimeUnit.SECONDS),
                    requests.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(Optional::isPresent).count());
            TokenResponse winner = results.stream().flatMap(Optional::stream).findFirst().orElseThrow();
            assertFalse(sessions.refresh(winner.refreshToken()).isPresent());
            assertEquals(1, countSessions(user.getId()));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void signedAccessTokenRequiresAuthenticationAndCannotReadAnotherUsersFixtureResource() throws Exception {
        User user = createUser(false);
        TokenResponse tokens = login(user, "192.0.2.41");
        String anonymousCorrelationId = "auth02-anonymous-1";

        MvcResult anonymous = mockMvc.perform(get("/api/v1/test/protected-user")
                        .header("X-Correlation-Id", anonymousCorrelationId))
                .andExpect(status().isUnauthorized()).andReturn();
        assertNormalizedError(anonymous, "AUTH_UNAUTHENTICATED", anonymousCorrelationId);

        String invalidJwtCorrelationId = "auth02-invalid-jwt-1";
        MvcResult invalidJwt = mockMvc.perform(get("/api/v1/test/protected-user")
                        .header("Authorization", "Bearer invalid")
                        .header("X-Correlation-Id", invalidJwtCorrelationId))
                .andExpect(status().isUnauthorized()).andReturn();
        assertNormalizedError(invalidJwt, "AUTH_UNAUTHENTICATED", invalidJwtCorrelationId);

        MvcResult allowed = mockMvc.perform(get("/api/v1/test/users/" + user.getId())
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk()).andReturn();
        assertEquals(user.getId().toString(), allowed.getResponse().getContentAsString());

        String forbiddenCorrelationId = "auth02-forbidden-1";
        MvcResult forbidden = mockMvc.perform(get("/api/v1/test/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Correlation-Id", forbiddenCorrelationId))
                .andExpect(status().isForbidden()).andReturn();
        assertNormalizedError(forbidden, "AUTH_ACCESS_DENIED", forbiddenCorrelationId);
        String forbiddenBody = forbidden.getResponse().getContentAsString();
        assertFalse(forbiddenBody.contains("User cannot access another user's resource"));
        assertFalse(forbiddenBody.contains(tokens.accessToken()));

        String missingResourceCorrelationId = "auth02-not-found-1";
        MvcResult missingResource = mockMvc.perform(get("/api/v1/test/missing")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Correlation-Id", missingResourceCorrelationId))
                .andExpect(status().isNotFound()).andReturn();
        assertNormalizedError(missingResource, "RESOURCE_NOT_FOUND", missingResourceCorrelationId);
        String missingResourceBody = missingResource.getResponse().getContentAsString();
        assertFalse(missingResourceBody.contains(tokens.accessToken()));
        assertFalse(missingResourceBody.contains("No static resource"));

        String internalErrorCorrelationId = "auth02-internal-error-1";
        MvcResult internalError = mockMvc.perform(get("/api/v1/test/internal-error")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Correlation-Id", internalErrorCorrelationId))
                .andExpect(status().isInternalServerError()).andReturn();
        assertNormalizedError(internalError, "INTERNAL_SERVER_ERROR", internalErrorCorrelationId);
        String internalErrorBody = internalError.getResponse().getContentAsString();
        assertFalse(internalErrorBody.contains("synthetic internal failure"));
        assertFalse(internalErrorBody.contains(tokens.accessToken()));
    }

    private void assertNormalizedError(MvcResult result, String code, String correlationId) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(code, body.path("code").asText());
        assertEquals(correlationId, body.path("correlationId").asText());
        assertEquals(correlationId, result.getResponse().getHeader("X-Correlation-Id"));
        assertEquals("{}", body.path("fieldErrors").toString());
        assertFalse(body.has("exception"));
        assertFalse(body.has("stackTrace"));
    }

    @Test
    void refreshRateLimitReturns429AndRetryAfter() throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            postRefresh("invalid-rate-limit-token", "198.51.100.77").andExpect(status().isUnauthorized());
        }
        MvcResult limited = postRefresh("invalid-rate-limit-token", "198.51.100.77")
                .andExpect(status().isTooManyRequests())
                .andReturn();
        assertNotNull(limited.getResponse().getHeader("Retry-After"));
        assertTrue(Long.parseLong(limited.getResponse().getHeader("Retry-After")) > 0);
    }

    private TokenResponse login(User user, String remoteAddress) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr(remoteAddress); return request; })
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginPayload(user.getEmail(), PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString()).path("tokens");
        return new TokenResponse(
                body.path("accessToken").asText(),
                body.path("refreshToken").asText(),
                body.path("tokenType").asText(),
                body.path("expiresIn").asLong());
    }

    private org.springframework.test.web.servlet.ResultActions postRefresh(String refreshToken, String remoteAddress)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .with(request -> { request.setRemoteAddr(remoteAddress); return request; })
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))));
    }

    private void assertInvalidRefresh(org.springframework.test.web.servlet.ResultActions request) throws Exception {
        MvcResult result = request.andExpect(status().isUnauthorized()).andReturn();
        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("AUTH_INVALID_REFRESH_TOKEN", error.path("code").asText());
        assertFalse(error.has("tokens"));
    }

    private User createUser(boolean disabled) {
        String email = "auth02-" + UUID.randomUUID() + "@example.test";
        User user = new User(email, passwordEncoder.encode(PASSWORD));
        if (disabled) user.disable();
        return users.saveAndFlush(user);
    }

    private long countSessions(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM auth_refresh_sessions WHERE user_id = ?", Long.class, userId);
    }

    private static String hash(String refreshToken) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(refreshToken.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private record LoginPayload(String email, String password) { }
    private record RefreshPayload(String refreshToken) { }
}
