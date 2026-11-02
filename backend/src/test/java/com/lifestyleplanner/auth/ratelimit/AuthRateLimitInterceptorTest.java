package com.lifestyleplanner.auth.ratelimit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifestyleplanner.auth.application.AuthService;
import com.lifestyleplanner.auth.controller.AuthController;
import com.lifestyleplanner.auth.dto.request.LoginRequest;
import com.lifestyleplanner.auth.dto.request.RegisterRequest;
import com.lifestyleplanner.auth.dto.response.AuthUserResponse;
import com.lifestyleplanner.auth.dto.response.LoginResponse;
import com.lifestyleplanner.auth.dto.response.RegisterResponse;
import com.lifestyleplanner.auth.ratelimit.AuthRateLimitProperties.Endpoint;
import com.lifestyleplanner.common.correlation.CorrelationIdConstants;
import com.lifestyleplanner.common.correlation.CorrelationIdFilter;
import com.lifestyleplanner.common.exception.GlobalExceptionHandler;
import com.lifestyleplanner.user.domain.enums.OnboardingStatus;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AuthRateLimitInterceptorTest {
    private static final String SOURCE_IP = "203.0.113.15";
    private static final String CORRELATION_ID = "auth-limit-test-1";

    private MutableClock clock;
    private AuthService authService;
    private AuthRateLimiter limiter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        authService = mock(AuthService.class);
        limiter = new AuthRateLimiter(properties(1, 60, 1, 600), clock);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AuthRateLimitInterceptor interceptor = new AuthRateLimitInterceptor(limiter, objectMapper, clock);

        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginResponse(
                new AuthUserResponse(UUID.randomUUID(), "person@example.test", OnboardingStatus.NOT_STARTED), null));
        when(authService.register(any(RegisterRequest.class))).thenReturn(new RegisterResponse(
                UUID.randomUUID(), "person@example.test", OnboardingStatus.NOT_STARTED));

        mockMvc = standaloneSetup(new AuthController(authService))
                .addInterceptors(interceptor)
                .addFilters(new CorrelationIdFilter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginReturnsGeneric429WithRetryAfterAndCorrelationId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(sourceAddress())
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(sourceAddress())
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.test\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
    }

    @Test
    void registerAndLoginHaveIndependentBucketsAndWindowExpiryReopensRequests() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(sourceAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(sourceAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\",\"password\":\"synthetic-password\",\"confirmPassword\":\"synthetic-password\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(sourceAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\",\"password\":\"synthetic-password\",\"confirmPassword\":\"synthetic-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"));

        clock.advance(Duration.ofSeconds(60));
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(sourceAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor sourceAddress() {
        return request -> {
            request.setRemoteAddr(SOURCE_IP);
            return request;
        };
    }

    private AuthRateLimitProperties properties(int loginCount, long loginSeconds, int registerCount, long registerSeconds) {
        return new AuthRateLimitProperties(
                100,
                loginCount,
                Duration.ofSeconds(loginSeconds),
                registerCount,
                Duration.ofSeconds(registerSeconds));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
