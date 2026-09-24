package com.github.webhook.repository;

import com.github.webhook.entity.Delivery;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    @EntityGraph(attributePaths = "subscription")
    List<Delivery> findByEventId(UUID eventId);

    long countByEventId(UUID eventId);
}
