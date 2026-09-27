package com.github.webhook.repository;

import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.DeliveryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    @EntityGraph(attributePaths = "subscription")
    List<Delivery> findByEventId(UUID eventId);

    long countByEventId(UUID eventId);

    @Query(value = """
        SELECT d.*
        FROM deliveries d
        JOIN subscriptions s ON s.id = d.subscription_id
        WHERE d.status IN ('PENDING', 'SENDING', 'FAILED')
          AND d.next_attempt_at <= now()
          AND (s.circuit_open_until IS NULL OR s.circuit_open_until <= now())
        ORDER BY d.next_attempt_at
        LIMIT :batchSize
        FOR UPDATE OF d SKIP LOCKED
        """, nativeQuery = true)
    List<Delivery> findDue(@Param("batchSize") int batchSize);

    long countByStatusIn(Collection<DeliveryStatus> statuses);
}
