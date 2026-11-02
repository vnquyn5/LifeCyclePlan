package com.lifestyleplanner.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.lifestyleplanner.common.correlation.CorrelationIdConstants;

import com.lifestyleplanner.common.response.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.security.web.access.AccessDeniedHandler;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;


/**
 * Chuẩn hóa lỗi 403 từ Spring Security.
 */
@Component
public class RestAccessDeniedHandler
        implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;


    public RestAccessDeniedHandler(
            ObjectMapper objectMapper
    ) {

        this.objectMapper =
                objectMapper;
    }


    @Override
    public void handle(

            HttpServletRequest request,

            HttpServletResponse response,

            AccessDeniedException accessDeniedException

    ) throws IOException {

        // HTTP 403.
        response.setStatus(
                HttpStatus.FORBIDDEN.value()
        );

        // JSON.
        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
                "UTF-8"
        );

        // Response chuẩn.
        ApiErrorResponse body =
                new ApiErrorResponse(

                        "AUTH_ACCESS_DENIED",

                        "Bạn không có quyền thực hiện thao tác này",

                        Map.of(),

                        MDC.get(
                                CorrelationIdConstants.MDC_KEY
                        ),

                        Instant.now()
                );

        objectMapper.writeValue(
                response.getWriter(),
                body
        );
    }
}