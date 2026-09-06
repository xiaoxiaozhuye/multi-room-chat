package com.multichat.websocket;

import com.multichat.common.api.ApiError;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** Maps internal command failures to a client-safe WebSocket ERROR payload. */
@Component
public class WebSocketExceptionMapper {
    public ApiError toError(Throwable exception) {
        if (exception instanceof BusinessException businessException) {
            return new ApiError(businessException.code(), businessException.getMessage(), businessException.details());
        }
        return new ApiError(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.message());
    }
}
