package com.multichat.common.web;

import com.multichat.common.logging.LogContext;
import com.multichat.infrastructure.metrics.HttpRequestMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    public static final String HEADER = "X-Request-Id";
    private final ApiResponseWriter responseWriter;
    private final HttpRequestMetrics httpRequestMetrics;

    public RequestIdFilter(ApiResponseWriter responseWriter) {
        this(responseWriter, null);
    }

    @Autowired
    public RequestIdFilter(ApiResponseWriter responseWriter, HttpRequestMetrics httpRequestMetrics) {
        this.responseWriter = responseWriter;
        this.httpRequestMetrics = httpRequestMetrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        } else if (!isUuid(requestId)) {
            response.setHeader(HEADER, UUID.randomUUID().toString());
            responseWriter.write(response, 400, "VALIDATION_FAILED", "Request validation failed.");
            recordMetrics(response);
            return;
        }
        request.setAttribute(HEADER, requestId);
        response.setHeader(HEADER, requestId);
        LogContext.putRequestId(requestId);
        extractResourceContext(request);
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("http_request_completed method={} path={} status={}", request.getMethod(),
                    request.getRequestURI(), response.getStatus());
            recordMetrics(response);
            LogContext.clearRequestContext();
        }
    }

    private void recordMetrics(HttpServletResponse response) {
        if (httpRequestMetrics != null) httpRequestMetrics.record(response.getStatus());
    }

    private boolean isUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.version() == 4;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void extractResourceContext(HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");
        for (int i = 0; i + 1 < segments.length; i++) {
            if ("rooms".equals(segments[i])) LogContext.putRoomId(segments[i + 1]);
            if ("messages".equals(segments[i]) || "review-messages".equals(segments[i])) {
                LogContext.putMessageId(segments[i + 1]);
            }
        }
    }
}
