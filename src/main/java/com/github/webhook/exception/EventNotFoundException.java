package com.github.webhook.exception;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {
    private final UUID id;
    public EventNotFoundException(UUID id) {
        super("Event not found with id");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
