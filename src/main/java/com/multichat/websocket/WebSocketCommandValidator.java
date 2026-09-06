package com.multichat.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multichat.common.api.ValidationError;
import com.multichat.common.exception.BusinessException;
import com.multichat.common.exception.ErrorCode;
import com.multichat.message.dto.SubmitMessageRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates every incoming WebSocket frame before command dispatch. It mirrors MVC
 * validation by converting parse, shape and bean validation errors to one public
 * validation error contract.
 */
@Component
public class WebSocketCommandValidator {
    private static final Set<String> COMMAND_TYPES = Set.of("SUBSCRIBE_ROOM", "UNSUBSCRIBE_ROOM", "CHAT_SUBMIT");
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("0|[1-9][0-9]*");

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public WebSocketCommandValidator(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public WebSocketCommand validate(String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (JsonProcessingException exception) {
            throw invalid("request", "MALFORMED_JSON");
        }
        if (root == null || !root.isObject()) throw invalid("request", "OBJECT_REQUIRED");

        String type = requiredText(root, "type");
        if (!COMMAND_TYPES.contains(type)) throw invalid("type", "UNKNOWN_COMMAND");
        UUID requestId = parseRequestId(requiredText(root, "requestId"));
        JsonNode payload = root.get("payload");
        if (payload == null || !payload.isObject()) throw invalid("payload", "OBJECT_REQUIRED");

        String roomId = null;
        if (type.equals("SUBSCRIBE_ROOM") || type.equals("UNSUBSCRIBE_ROOM") || type.equals("CHAT_SUBMIT")) {
            roomId = requiredText(payload, "roomId");
            validateUuid(roomId, "roomId");
        }
        if (type.equals("SUBSCRIBE_ROOM")) {
            validateSequence(payload, "lastMessageSeq");
            validateSequence(payload, "lastNotificationSeq");
        }
        if (type.equals("CHAT_SUBMIT")) validateSubmit(payload);
        return new WebSocketCommand(type, requestId, payload);
    }

    private void validateSubmit(JsonNode payload) {
        SubmitMessageRequest request;
        try {
            request = objectMapper.treeToValue(payload, SubmitMessageRequest.class);
        } catch (JsonProcessingException exception) {
            throw invalid("payload", "INVALID");
        }
        List<ValidationError> details = validator.validate(request).stream()
                .map(this::toError).toList();
        if (!details.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, details);
    }

    private ValidationError toError(ConstraintViolation<?> violation) {
        return new ValidationError(violation.getPropertyPath().toString(), "INVALID");
    }

    private void validateSequence(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value != null && (!value.isTextual() || !NON_NEGATIVE_INTEGER.matcher(value.textValue()).matches())) {
            throw invalid(field, "NON_NEGATIVE_INTEGER_REQUIRED");
        }
    }

    private UUID parseRequestId(String value) {
        try {
            UUID requestId = UUID.fromString(value);
            if (requestId.version() != 4) throw invalid("requestId", "UUID_V4_REQUIRED");
            return requestId;
        } catch (IllegalArgumentException exception) {
            throw invalid("requestId", "UUID_V4_REQUIRED");
        }
    }

    private void validateUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(field, "UUID_REQUIRED");
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw invalid(field, "REQUIRED");
        return value.textValue();
    }

    private BusinessException invalid(String field, String reason) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(new ValidationError(field, reason)));
    }
}
