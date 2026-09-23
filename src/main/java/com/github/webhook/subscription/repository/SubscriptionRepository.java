package com.github.webhook.subscription.repository;

import com.github.webhook.domain.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription,UUID> {
}
