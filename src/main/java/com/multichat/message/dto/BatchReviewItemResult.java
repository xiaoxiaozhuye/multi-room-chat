package com.multichat.message.dto;

/** A per-message batch outcome; business failures never abort the other rows. */
public record BatchReviewItemResult(String messageId, String reviewResult, String messageStatus, String errorCode) {
}
