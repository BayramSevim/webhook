package com.github.webhook.service;

import java.time.Instant;

public record SendResult(
        Instant attemptedAt,
        Integer responseStatus,
        String errorMessage,
        int durationMs
) {
    public boolean succeeded() {
        return errorMessage == null;
    }
}