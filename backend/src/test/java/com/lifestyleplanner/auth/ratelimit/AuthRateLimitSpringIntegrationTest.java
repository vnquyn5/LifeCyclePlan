package com.lifestyleplanner.auth.ratelimit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.lifestyleplanner.user.repository.UserRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lifestyleplanner.auth.application.AuthService;
import com.lifestyleplanner.auth.controller.AuthController;
import com.lifestyleplanner.auth.dto.request.LoginRequest;
import com.lifestyleplanner.auth.dto.request.RegisterRequest;
import com.lifestyleplanner.auth.dto.response.AuthUserResponse;
import com.lifestyleplanner.auth.dto.response.LoginResponse;
import com.lifestyleplanner.auth.dto.response.RegisterResponse;
import com.lifestyleplanner.auth.session.application.AuthSessionService;
import com.lifestyleplanner.common.correlation.CorrelationIdConstants;
import com.lifestyleplanner.common.correlation.CorrelationIdFilter;
import com.lifestyleplanner.common.exception.GlobalExceptionHandler;
import com.lifestyleplanner.security.RestAccessDeniedHandler;
import com.lifestyleplanner.security.RestAuthenticationEntryPoint;
import com.lifestyleplanner.security.SecurityConfig;
import com.lifestyleplanner.user.domain.enums.OnboardingStatus;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc
@Import({
        AuthRateLimitConfiguration.class,
        AuthRateLimitWebMvcConfiguration.class,
        AuthRateLimitInterceptor.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.max-tracked-buckets=100",
        "app.security.auth-rate-limit.login-max-requests=2",
        "app.security.auth-rate-limit.login-window=60s",
        "app.security.auth-rate-limit.register-max-requests=1",
        "app.security.auth-rate-limit.register-window=600s",
        "app.security.auth-rate-limit.refresh-max-requests=30",
        "app.security.auth-rate-limit.refresh-window=60s"
})
class AuthRateLimitSpringIntegrationTest {
    private static final String CORRELATION_ID = "auth-spring-integration-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuthSessionService authSessionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserRepository userRepository;

    @Test
    void mvcConfigurationAppliesLoginLimitAndReturnsTheApi429Contract() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginResponse(
                new AuthUserResponse(UUID.randomUUID(), "person@example.test", OnboardingStatus.NOT_STARTED), null));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(sourceAddress("198.51.100.10"))
                            .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"person@example.test\",\"password\":\"synthetic-password\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(sourceAddress("198.51.100.10"))
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.test\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
    }

    @Test
    void mvcConfigurationAppliesTheIndependentRegisterLimit() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(new RegisterResponse(
                UUID.randomUUID(), "person@example.test", OnboardingStatus.NOT_STARTED));

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(sourceAddress("198.51.100.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.test\",\"password\":\"synthetic-password\",\"confirmPassword\":\"synthetic-password\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(sourceAddress("198.51.100.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.test\",\"password\":\"synthetic-password\",\"confirmPassword\":\"synthetic-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));
    }

    private RequestPostProcessor sourceAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
