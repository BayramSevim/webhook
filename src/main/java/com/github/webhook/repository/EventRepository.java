package com.github.webhook.repository;

import com.github.webhook.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event,UUID> {
    Optional<Event> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
}
