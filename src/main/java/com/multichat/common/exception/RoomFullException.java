package com.multichat.common.exception;

/** Raised when a membership activation would exceed a room's capacity. */
public class RoomFullException extends BusinessException {
    public RoomFullException() {
        super(ErrorCode.ROOM_FULL);
    }
}
