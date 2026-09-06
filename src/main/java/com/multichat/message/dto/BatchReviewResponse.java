package com.multichat.message.dto;

import java.util.List;

public record BatchReviewResponse(List<BatchReviewItemResult> results) {
}
