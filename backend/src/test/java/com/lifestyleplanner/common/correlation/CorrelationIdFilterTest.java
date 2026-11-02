package com.lifestyleplanner.common.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesSafeCorrelationIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdConstants.HEADER_NAME, "mobile-request_123.abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("mobile-request_123.abc", response.getHeader(CorrelationIdConstants.HEADER_NAME));
    }

    @Test
    void replacesCorrelationIdsThatCouldInjectHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdConstants.HEADER_NAME, "bad\r\nX-Forged: yes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> filter.doFilter(request, response, new MockFilterChain()));

        String correlationId = (String) response.getHeader(CorrelationIdConstants.HEADER_NAME);
        assertNotEquals("bad\r\nX-Forged: yes", correlationId);
        assertEquals(null, response.getHeader("X-Forged"));
        assertDoesNotThrow(() -> UUID.fromString(correlationId));
    }

    @Test
    void replacesOversizedCorrelationIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdConstants.HEADER_NAME, "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String correlationId = (String) response.getHeader(CorrelationIdConstants.HEADER_NAME);
        assertNull(response.getHeader("X-Forged"));
        assertDoesNotThrow(() -> UUID.fromString(correlationId));
    }
}
