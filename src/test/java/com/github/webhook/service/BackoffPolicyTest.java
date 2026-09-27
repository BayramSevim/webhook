package com.github.webhook.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class BackoffPolicyTest {
    private final BackoffPolicy policy = new BackoffPolicy();

    @Test
    void doesNotGiveUpBeforeFifthAttempt() {
        assertThat(policy.shouldGiveUp(1)).isFalse();
        assertThat(policy.shouldGiveUp(4)).isFalse();
    }

    @Test
    void givesUpFromFifthAttempt() {
        assertThat(policy.shouldGiveUp(5)).isTrue();
        assertThat(policy.shouldGiveUp(6)).isTrue();
    }

    @RepeatedTest(50)
    void firstDelayIsAroundTenSeconds() {
        assertThat(policy.delayAfter(1))
                .isBetween(Duration.ofSeconds(8), Duration.ofSeconds(12));
    }

    @RepeatedTest(50)
    void delayDoublesEachAttempt() {
        assertThat(policy.delayAfter(2)).isBetween(Duration.ofSeconds(16), Duration.ofSeconds(24));
        assertThat(policy.delayAfter(3)).isBetween(Duration.ofSeconds(32), Duration.ofSeconds(48));
        assertThat(policy.delayAfter(4)).isBetween(Duration.ofSeconds(64), Duration.ofSeconds(96));
    }

    @Test
    void delaysAreJittered() {
        Set<Duration> delays = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            delays.add(policy.delayAfter(1));
        }
        assertThat(delays).hasSizeGreaterThan(1);
    }
}
