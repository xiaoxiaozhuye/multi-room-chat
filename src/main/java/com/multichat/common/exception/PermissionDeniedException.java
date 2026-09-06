package com.multichat.common.exception;

/** Raised for an authenticated caller that lacks the required permission. */
public class PermissionDeniedException extends BusinessException {
    public PermissionDeniedException() {
        super(ErrorCode.FORBIDDEN);
    }

    public PermissionDeniedException(String code, String message) {
        super(code, message, ErrorCode.FORBIDDEN.status());
    }
}
