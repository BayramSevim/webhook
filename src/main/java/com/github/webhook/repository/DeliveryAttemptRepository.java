package com.github.webhook.repository;

import com.github.webhook.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
    long countByDeliveryId(UUID deliveryId);
}
