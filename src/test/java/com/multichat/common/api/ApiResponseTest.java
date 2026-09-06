package com.multichat.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {
    @Test
    void failureAlwaysUsesTheThreeFieldEnvelope() throws Exception {
        String json = new ObjectMapper().writeValueAsString(ApiResponse.failure("ROOM_FULL", "The room is full."));
        JsonNode body = new ObjectMapper().readTree(json);

        assertEquals("ROOM_FULL", body.get("code").asText());
        assertEquals("The room is full.", body.get("message").asText());
        assertTrue(body.has("data"));
        assertTrue(body.get("data").isNull());
    }
}
