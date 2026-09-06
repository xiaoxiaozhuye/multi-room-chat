package com.multichat.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The only REST response envelope exposed by the application.  HTTP status conveys
 * transport semantics; {@code code} is the stable value clients use for behaviour.
 */
public record ApiResponse<T>(String code, String message, @JsonInclude(JsonInclude.Include.ALWAYS) T data) {
    public static final String SUCCESS = "SUCCESS";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS, "OK", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(SUCCESS, message, data);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
