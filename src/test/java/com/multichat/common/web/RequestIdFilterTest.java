package com.multichat.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.common.logging.LogContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter(new ApiResponseWriter(new ObjectMapper()));

    @Test
    void createsResponseAndLogCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rooms/room-1/messages/message-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertEquals(req.getAttribute(RequestIdFilter.HEADER), LogContextValue.requestId());
            assertEquals("room-1", LogContextValue.roomId());
            assertEquals("message-1", LogContextValue.messageId());
        });

        UUID.fromString(response.getHeader(RequestIdFilter.HEADER));
        assertNull(LogContextValue.requestId());
    }

    @Test
    void rejectsInvalidCorrelationIdUsingThePublicEnvelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/ping");
        request.addHeader(RequestIdFilter.HEADER, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("chain should not run"); });

        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_FAILED", new ObjectMapper().readTree(response.getContentAsString()).get("code").asText());
    }

    private static final class LogContextValue {
        private static String requestId() { return org.slf4j.MDC.get(LogContext.REQUEST_ID); }
        private static String roomId() { return org.slf4j.MDC.get(LogContext.ROOM_ID); }
        private static String messageId() { return org.slf4j.MDC.get(LogContext.MESSAGE_ID); }
    }
}
