package com.github.webhook.domain.exception;

import java.util.UUID;

public class SubscriptionNotFoundException extends RuntimeException {
    private final UUID id;
    public SubscriptionNotFoundException(UUID id) {
        super("Subscription not found with id");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
