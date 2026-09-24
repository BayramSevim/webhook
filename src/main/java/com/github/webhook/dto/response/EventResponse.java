package com.github.webhook.dto.response;

import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.Event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String tenantId,
        String eventType,
        String payload,
        Instant createdAt,
        int deliveriesSize
) {
    public static EventResponse from(Event e,int deliveriesSize) {
        return new EventResponse(
                e.getId(),
                e.getTenantId(),
                e.getEventType(),
                e.getPayload(),
                e.getCreatedAt(),
                deliveriesSize
        );
    }
}
