package com.multichat.common.exception;

import org.springframework.http.HttpStatus;

/** Public error contract. Messages here are deliberately safe to return to clients. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not allowed to access this room."),
    ROOM_FULL(HttpStatus.CONFLICT, "The room has reached its member limit."),
    REVIEW_ALREADY_PROCESSED(HttpStatus.CONFLICT, "This message has already been reviewed."),
    ROOM_STATE_CONFLICT(HttpStatus.CONFLICT, "The requested room state conflicts with its current state."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "The server could not process this request."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
