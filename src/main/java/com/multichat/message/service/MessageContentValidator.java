package com.multichat.message.service;

import com.multichat.common.api.ValidationError;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

/** Applies the protocol's Unicode-scalar input boundary, rather than UTF-16 length. */
@Component
public class MessageContentValidator {
    public void requireSendable(String content) {
        if (content == null || content.isBlank() || hasUnpairedSurrogate(content)
                || content.codePointCount(0, content.length()) > 320) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    List.of(new ValidationError("content", "INVALID")));
        }
    }

    private boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 == value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) return true;
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
