package com.lifestyleplanner.auth.session.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import com.lifestyleplanner.auth.session.domain.entity.AuthRefreshSession;
import com.lifestyleplanner.auth.session.domain.entity.AuthRefreshToken;
import com.lifestyleplanner.auth.session.domain.entity.SessionRevocationReason;
import com.lifestyleplanner.auth.session.domain.valueobject.TokenHash;
import com.lifestyleplanner.auth.session.repository.AuthRefreshSessionRepository;
import com.lifestyleplanner.auth.session.repository.AuthRefreshTokenRepository;
import com.lifestyleplanner.security.JwtProperties;
import com.lifestyleplanner.user.domain.entity.User;

class AuthSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final String OLD_SECRET = "old-refresh-secret";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    private AuthRefreshSessionRepository sessions;
    private AuthRefreshTokenRepository tokens;
    private JwtEncoder encoder;
    private AuthSessionService service;
    private User user;

    @BeforeEach
    void setUp() {
        sessions = mock(AuthRefreshSessionRepository.class);
        tokens = mock(AuthRefreshTokenRepository.class);
        encoder = mock(JwtEncoder.class);
        user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.canLogin()).thenReturn(true);
        when(encoder.encode(any())).thenAnswer(invocation -> Jwt.withTokenValue("signed-access-token")
                .header("alg", "RS256").subject(USER_ID.toString()).issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(900)).build());
        service = new AuthSessionService(sessions, tokens, encoder,
                new JwtProperties("lifecycleplan-backend", Duration.ofMinutes(15), Duration.ofDays(7),
                        "/tmp/private.pem", "/tmp/public.pem"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueStoresOnlyAHashAndReturnsTheConfiguredTokenPair() {
        when(tokens.saveAndFlush(any(AuthRefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.issue(user);

        var saved = org.mockito.ArgumentCaptor.forClass(AuthRefreshToken.class);
        verify(tokens).saveAndFlush(saved.capture());
        assertNotEquals(response.refreshToken(), saved.getValue().getTokenHash().value());
        assertEquals(64, saved.getValue().getTokenHash().value().length());
        assertEquals(NOW.plus(Duration.ofDays(7)), saved.getValue().getExpiresAt());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());
        assertEquals("signed-access-token", response.accessToken());
        assertTrue(response.refreshToken().matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    void refreshRotatesTheRefreshTokenAndKeepsTheSessionFamily() throws Exception {
        TokenHash oldHash = hash(OLD_SECRET);
        AuthRefreshToken oldToken = new AuthRefreshToken(user, SESSION_ID, oldHash, NOW.minusSeconds(1),
                NOW.plus(Duration.ofDays(7)));
        setId(oldToken, UUID.randomUUID());
        AuthRefreshSession session = new AuthRefreshSession(SESSION_ID, user, NOW.minusSeconds(10));
        AtomicReference<AuthRefreshToken> replacement = new AtomicReference<>();
        when(tokens.findSessionIdByTokenHash(oldHash)).thenReturn(Optional.of(SESSION_ID));
        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(tokens.findByTokenHashForUpdate(oldHash)).thenReturn(Optional.of(oldToken));
        when(tokens.findAllBySessionId(SESSION_ID)).thenReturn(List.of(oldToken));
        when(tokens.saveAndFlush(any(AuthRefreshToken.class))).thenAnswer(invocation -> {
            AuthRefreshToken newToken = invocation.getArgument(0);
            setId(newToken, UUID.randomUUID());
            replacement.set(newToken);
            return newToken;
        });
        when(tokens.findByTokenHash(any(TokenHash.class))).thenAnswer(invocation -> {
            TokenHash requestedHash = invocation.getArgument(0);
            return requestedHash.equals(oldHash) ? Optional.of(oldToken)
                    : Optional.ofNullable(replacement.get());
        });

        var result = service.refresh(OLD_SECRET);

        assertTrue(result.isPresent());
        assertNotEquals(OLD_SECRET, result.get().refreshToken());
        assertEquals(result.get().accessToken(), "signed-access-token");
        assertTrue(oldToken.getRevokedAt().equals(NOW));
        assertTrue(oldToken.getReplacedByTokenId() != null);
        assertFalse(session.isRevoked());
    }

    @Test
    void reuseRevokesTheSessionFamilyAndReturnsNoTokens() {
        TokenHash oldHash = hash(OLD_SECRET);
        AuthRefreshToken oldToken = mock(AuthRefreshToken.class);
        AuthRefreshSession session = mock(AuthRefreshSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(oldToken.getReplacedByTokenId()).thenReturn(UUID.randomUUID());
        when(oldToken.getSessionId()).thenReturn(SESSION_ID);
        when(tokens.findSessionIdByTokenHash(oldHash)).thenReturn(Optional.of(SESSION_ID));
        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(tokens.findByTokenHashForUpdate(oldHash)).thenReturn(Optional.of(oldToken));
        when(tokens.findAllBySessionId(SESSION_ID)).thenReturn(List.of(oldToken));

        assertFalse(service.refresh(OLD_SECRET).isPresent());

        verify(session).revoke(NOW, SessionRevocationReason.REFRESH_REUSE);
        verify(oldToken).revoke(NOW);
        verify(encoder, never()).encode(any());
    }

    private static TokenHash hash(String secret) {
        try {
            return new TokenHash(java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(secret.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setId(AuthRefreshToken token, UUID id) throws Exception {
        Field idField = AuthRefreshToken.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(token, id);
    }
}
