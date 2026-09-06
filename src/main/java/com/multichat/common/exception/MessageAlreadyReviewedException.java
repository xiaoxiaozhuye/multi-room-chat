package com.multichat.common.exception;

/** Raised when a conditional review update finds that another actor already decided it. */
public class MessageAlreadyReviewedException extends BusinessException {
    public MessageAlreadyReviewedException() {
        super(ErrorCode.REVIEW_ALREADY_PROCESSED);
    }
}
