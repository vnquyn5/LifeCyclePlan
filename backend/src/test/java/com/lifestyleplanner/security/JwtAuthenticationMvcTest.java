package com.lifestyleplanner.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import com.lifestyleplanner.common.exception.GlobalExceptionHandler;
import com.lifestyleplanner.auth.ratelimit.AuthRateLimiter;
import com.lifestyleplanner.user.domain.entity.User;
import com.lifestyleplanner.user.repository.UserRepository;

@WebMvcTest(ProtectedAuthTestController.class)
@AutoConfigureMockMvc
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        CurrentUserProvider.class, GlobalExceptionHandler.class, ProtectedAuthTestController.class,
        JwtAuthenticationMvcTest.ClockTestConfiguration.class })
class JwtAuthenticationMvcTest {
    private static final UUID USER_ID = UUID.fromString("1155d044-ef82-463d-a318-e265fd748dd1");
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private AuthRateLimiter authRateLimiter;

    @BeforeEach
    void setUp() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getEmail()).thenReturn("synthetic@example.test");
        when(user.canLogin()).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtDecoder.decode("valid-test-access-token")).thenReturn(Jwt.withTokenValue("valid-test-access-token")
                .header("alg", "RS256").subject(USER_ID.toString()).issuedAt(NOW).expiresAt(NOW.plusSeconds(900))
                .build());
    }

    @Test
    void protectedRouteRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected-user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteReceivesTheDatabaseBackedAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected-user")
                        .header("Authorization", "Bearer valid-test-access-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(USER_ID.toString()));
    }

    @Configuration(proxyBeanMethods = false)
    static class ClockTestConfiguration {
        @Bean
        Clock authRateLimitClock() {
            return Clock.systemUTC();
        }
    }
}
