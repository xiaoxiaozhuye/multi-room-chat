package com.multichat.common.web;

import com.multichat.common.api.ApiResponse;
import com.multichat.common.api.ValidationError;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException exception) {
        Object data = exception.details().isEmpty() ? null : Map.of("details", exception.details());
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.failure(exception.code(), exception.getMessage(), data));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> handleInvalidBody(MethodArgumentNotValidException exception) {
        List<ValidationError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toValidationError).toList();
        return validationFailure(details);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Object>> handleInvalidInput(Exception exception) {
        String field = fieldName(exception);
        return validationFailure(List.of(new ValidationError(field, "INVALID")));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.message()));
    }

    private ResponseEntity<ApiResponse<Object>> validationFailure(List<ValidationError> details) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.message(),
                Map.of("details", details)));
    }

    private ValidationError toValidationError(FieldError error) {
        return new ValidationError(error.getField(), error.getCode() == null ? "INVALID" : error.getCode());
    }

    private String fieldName(Exception exception) {
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) return mismatch.getName();
        if (exception instanceof MissingServletRequestParameterException missing) return missing.getParameterName();
        if (exception instanceof MissingRequestHeaderException missing) return missing.getHeaderName();
        return "request";
    }
}
