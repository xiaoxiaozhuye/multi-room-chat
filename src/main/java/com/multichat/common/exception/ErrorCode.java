package com.multichat.common.exception;

import org.springframework.http.HttpStatus;

/** Public error contract. Messages here are deliberately safe to return to clients. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not allowed to access this room."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested room was not found."),
    ADMIN_ROLE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "The target user must be an active room administrator."),
    ROOM_FULL(HttpStatus.CONFLICT, "The room has reached its member limit."),
    REVIEW_ALREADY_PROCESSED(HttpStatus.CONFLICT, "This message has already been reviewed."),
    ROOM_STATE_CONFLICT(HttpStatus.CONFLICT, "The requested room state conflicts with its current state."),
    ROOM_PAUSED(HttpStatus.UNPROCESSABLE_ENTITY, "The room is paused and does not accept this operation."),
    ROOM_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "The room is closed and does not accept this operation."),
    ROOM_DELETED(HttpStatus.UNPROCESSABLE_ENTITY, "The room has been deleted."),
    MEMBERSHIP_ALREADY_ACTIVE(HttpStatus.CONFLICT, "The user is already a member of this room."),
    JOIN_REQUEST_ALREADY_PENDING(HttpStatus.CONFLICT, "The user already has a pending join request."),
    MEMBER_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "The join request was not found."),
    MEMBER_REQUEST_ALREADY_PROCESSED(HttpStatus.CONFLICT, "The join request has already been processed."),
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
