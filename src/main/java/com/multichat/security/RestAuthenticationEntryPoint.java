package com.multichat.security;

import com.multichat.common.exception.ErrorCode;
import com.multichat.common.web.ApiResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Converts authentication failures raised before MVC into the public API envelope. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ApiResponseWriter responseWriter;

    public RestAuthenticationEntryPoint(ApiResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        responseWriter.write(response, ErrorCode.UNAUTHENTICATED.status().value(),
                ErrorCode.UNAUTHENTICATED.name(), ErrorCode.UNAUTHENTICATED.message());
    }
}
