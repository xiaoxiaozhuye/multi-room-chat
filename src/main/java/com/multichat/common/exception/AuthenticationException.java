package com.multichat.common.exception;

/** Raised when an endpoint needs an authenticated identity. */
public class AuthenticationException extends BusinessException {
    public AuthenticationException() {
        super(ErrorCode.UNAUTHENTICATED);
    }

    public AuthenticationException(String message) {
        super(ErrorCode.UNAUTHENTICATED.name(), message, ErrorCode.UNAUTHENTICATED.status());
    }
}
