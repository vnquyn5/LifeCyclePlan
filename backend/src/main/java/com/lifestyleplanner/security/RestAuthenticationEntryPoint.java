package com.lifestyleplanner.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.jboss.logging.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifestyleplanner.common.correlation.CorrelationIdConstants;
import com.lifestyleplanner.common.response.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component 
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper){
        this.objectMapper = objectMapper;
    }

    @Override 
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY) != null
            ? MDC.get(CorrelationIdConstants.MDC_KEY).toString()
            : null;
        ApiErrorResponse body = new ApiErrorResponse(
            "AUTH_UNAUTHENTICATED",
            "Yeu cau xac thuc",
            Map.of(),
            correlationId,
            Instant.now());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
