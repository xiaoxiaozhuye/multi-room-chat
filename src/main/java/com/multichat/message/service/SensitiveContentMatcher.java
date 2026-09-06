package com.multichat.message.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Compiles configured literal words once at startup without exposing matches to callers. */
@Component
public class SensitiveContentMatcher {
    private final List<String> words;
    private final String matchMode;

    public SensitiveContentMatcher(ModerationProperties properties) {
        this.words = properties.sensitiveWords().stream()
                .filter(word -> word != null && !word.isBlank())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
        this.matchMode = properties.matchMode().trim().toUpperCase(Locale.ROOT);
        if (!"CONTAINS".equals(matchMode) && !"EXACT".equals(matchMode)) {
            throw new IllegalArgumentException("chat.moderation.match-mode must be CONTAINS or EXACT");
        }
    }

    public boolean matches(String content) {
        String candidate = content.toLowerCase(Locale.ROOT);
        return "EXACT".equals(matchMode)
                ? words.stream().anyMatch(candidate::equals)
                : words.stream().anyMatch(candidate::contains);
    }
}
