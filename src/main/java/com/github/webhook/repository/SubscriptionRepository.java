package com.github.webhook.repository;

import com.github.webhook.dto.request.CreateSubscriptionRequest;
import com.github.webhook.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription,UUID> {

    @Query(nativeQuery = true, value = """
        SELECT *
        FROM subscriptions
        WHERE active = true
          AND tenant_id = :tenantId
          AND event_types @> cast(array[:eventType] as text[])
        """)
    List<Subscription> findActiveByTenantAndEventType(
            @Param("tenantId") String tenantId,
            @Param("eventType") String eventType
    );
}
