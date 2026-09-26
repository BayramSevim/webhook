package com.github.webhook.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class BackoffPolicy {

    private static final Duration BASE = Duration.ofSeconds(10);
    private static final int MAX_ATTEMPTS = 5;

    public boolean shouldGiveUp(int attemptCount) {
        return attemptCount >= MAX_ATTEMPTS;
    }

    public Duration delayAfter(int attemptCount) {
        Duration baseDuration = BASE.multipliedBy((long) Math.pow(2,attemptCount -1 ));
        return Duration.ofMillis((long) (baseDuration.toMillis() * ThreadLocalRandom.current().nextDouble(0.8, 1.2)));
    }
}
