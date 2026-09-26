package com.github.webhook.repository;

import com.github.webhook.entity.Delivery;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    @EntityGraph(attributePaths = "subscription")
    List<Delivery> findByEventId(UUID eventId);

    long countByEventId(UUID eventId);

    @Query(value = """
        SELECT *
        FROM deliveries 
        WHERE status IN('PENDING','SENDING','FAILED')
        AND next_attempt_at <= now()
        ORDER BY next_attempt_at 
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Delivery> findDue(@Param("batchSize") int batchSize);
}
