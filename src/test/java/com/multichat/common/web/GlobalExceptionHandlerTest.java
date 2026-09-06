package com.multichat.common.web;

import com.multichat.common.api.ApiResponse;
import com.multichat.common.exception.RoomFullException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionsKeepTheirPublicCodeAndHttpStatus() {
        var response = handler.handleBusiness(new RoomFullException());

        assertEquals(409, response.getStatusCode().value());
        assertEquals("ROOM_FULL", response.getBody().code());
        assertEquals("The room has reached its member limit.", response.getBody().message());
    }

    @Test
    void unexpectedExceptionsNeverExposeTheirMessage() {
        var response = handler.handleUnexpected(new IllegalStateException("database password leaked"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals("The server could not process this request.", response.getBody().message());
    }
}
