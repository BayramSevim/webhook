package com.github.webhook.dto.response;

import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.DeliveryStatus;
import com.github.webhook.entity.Event;

import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID subscriptionId,
        String url,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeliveryResponse from(Delivery d) {
        return new DeliveryResponse(
                 d.getId(),
                d.getSubscription().getId(),
                d.getSubscription().getUrl(),
                d.getStatus(),
                d.getAttemptCount(),
                d.getNextAttemptAt(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
