package com.multichat.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Writes the same safe envelope from security filters that MVC advice cannot reach. */
@Component
public class ApiResponseWriter {
    private final ObjectMapper objectMapper;

    public ApiResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(code, message));
    }
}
