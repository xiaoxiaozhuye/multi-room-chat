package com.multichat.security;

import com.multichat.common.exception.ErrorCode;
import com.multichat.common.web.ApiResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Converts authorization failures raised before MVC into the public API envelope. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final ApiResponseWriter responseWriter;

    public RestAccessDeniedHandler(ApiResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        responseWriter.write(response, ErrorCode.FORBIDDEN.status().value(),
                ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.message());
    }
}
