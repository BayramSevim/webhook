package com.github.webhook.domain.dto.response;

import com.github.webhook.domain.entity.Subscription;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String tenantId,
        String url,
        List<String> eventTypes,
        boolean active,
        Instant createdAt
) {
    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
                s.getId(),
                s.getTenantId(),
                s.getUrl(),
                s.getEventTypes(),
                s.isActive(),
                s.getCreatedAt()
        );
    }
}