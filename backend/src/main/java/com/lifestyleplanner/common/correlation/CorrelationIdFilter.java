package com.lifestyleplanner.common.correlation;

import java.io.IOException;
import jakarta.servlet.ServletException;
import java.util.UUID;

import org.jboss.logging.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@Component 
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override 
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationIdConstants.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()){
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
    
}
