package com.multichat.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** One target room outcome; a failure in one room never hides other outcomes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminRoomMessageItemResult(UUID roomId, String deliveryStatus, UUID messageId,
                                         Long roomSeq, Long notificationSeq, String messageStatus,
                                         String errorCode) {
    public static AdminRoomMessageItemResult noPermission(UUID roomId) {
        return new AdminRoomMessageItemResult(roomId, "NO_PERMISSION", null, null, null, null,
                "ROOM_ACCESS_DENIED");
    }

    public static AdminRoomMessageItemResult roomNotSendable(UUID roomId, String errorCode) {
        return new AdminRoomMessageItemResult(roomId, "ROOM_NOT_SENDABLE", null, null, null, null, errorCode);
    }
}
