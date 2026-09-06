package com.multichat.room.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** All fields are optional, but a request must change at least one field. */
public record UpdateRoomRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 10_000) String description,
        @Min(1) @Max(100_000) Integer maxMembers,
        String joinMode,
        @JsonAlias("status") String roomStatus) {

    public boolean isEmpty() {
        return name == null && description == null && maxMembers == null
                && joinMode == null && roomStatus == null;
    }
}
