package com.github.webhook.repository;

import com.github.webhook.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
}
