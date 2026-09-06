package com.multichat.common.exception;

import com.multichat.common.api.ValidationError;
import org.springframework.http.HttpStatus;

import java.util.List;

public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final List<ValidationError> details;

    public BusinessException(String code, String message, HttpStatus status) {
        this(code, message, status, List.of());
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode.name(), errorCode.message(), errorCode.status());
    }

    public BusinessException(ErrorCode errorCode, List<ValidationError> details) {
        this(errorCode.name(), errorCode.message(), errorCode.status(), details);
    }

    public BusinessException(String code, String message, HttpStatus status, List<ValidationError> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = List.copyOf(details);
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public List<ValidationError> details() { return details; }
}
