package com.lifestyleplanner.auth.ratelimit;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifestyleplanner.auth.exception.AuthErrorCode;
import com.lifestyleplanner.auth.ratelimit.AuthRateLimitProperties.Endpoint;
import com.lifestyleplanner.common.correlation.CorrelationIdConstants;
import com.lifestyleplanner.common.response.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {
    private final AuthRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuthRateLimitInterceptor(AuthRateLimiter rateLimiter, ObjectMapper objectMapper, Clock authRateLimitClock) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.clock = authRateLimitClock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        Endpoint endpoint = endpointFor(request.getRequestURI());
        if (endpoint == null) {
            return true;
        }

        AuthRateLimiter.Decision decision = rateLimiter.consume(endpoint, request.getRemoteAddr());
        if (decision.allowed()) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));

        ApiErrorResponse body = new ApiErrorResponse(
                AuthErrorCode.RATE_LIMITED.getCode(),
                AuthErrorCode.RATE_LIMITED.getMessage(),
                Map.of(),
                response.getHeader(CorrelationIdConstants.HEADER_NAME),
                Instant.now(clock));
        objectMapper.writeValue(response.getWriter(), body);
        return false;
    }

    private Endpoint endpointFor(String requestUri) {
        return switch (requestUri) {
            case "/api/v1/auth/login" -> Endpoint.LOGIN;
            case "/api/v1/auth/register" -> Endpoint.REGISTER;
            case "/api/v1/auth/refresh" -> Endpoint.REFRESH;
            default -> null;
        };
    }
}
