package com.lifestyleplanner.common.correlation;

import java.io.IOException;
import jakarta.servlet.ServletException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@Component 
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final int MAX_CORRELATION_ID_LENGTH = 64;


    @Override 
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationIdConstants.HEADER_NAME);
        if (!isSafeCorrelationId(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
            response.setHeader(CorrelationIdConstants.HEADER_NAME, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIdConstants.MDC_KEY);
        }
    }

    private boolean isSafeCorrelationId(String correlationId) {
        return correlationId != null
                && correlationId.length() <= MAX_CORRELATION_ID_LENGTH
                && correlationId.matches("[A-Za-z0-9._-]+");
    }
    
}
