package com.github.webhook.service;


import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.DeliveryAttempt;
import com.github.webhook.entity.Subscription;
import com.github.webhook.repository.DeliveryAttemptRepository;
import com.github.webhook.repository.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeliveryStateService {

    private static final Duration LEASE = Duration.ofMinutes(2);
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final BackoffPolicy backoffPolicy;

    private static final int CIRCUIT_THRESHOLD = 5;
    private static final Duration CIRCUIT_OPEN_FOR = Duration.ofMinutes(1);

    public DeliveryStateService(DeliveryRepository deliveryRepository, DeliveryAttemptRepository deliveryAttemptRepository, BackoffPolicy backoffPolicy) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.backoffPolicy = backoffPolicy;
    }

    @Transactional
    public List<DeliveryJob> claimDue(int batchSize) {
        return deliveryRepository.findDue(batchSize)
                .stream()
                .map(delivery -> {
                    delivery.markSending(Instant.now().plus(LEASE));
                    return new DeliveryJob(
                            delivery.getId(),
                            delivery.getSubscription().getUrl(),
                            delivery.getSubscription().getSecret(),
                            delivery.getEvent().getEventType(),
                            delivery.getEvent().getPayload()
                    );
                }).toList();
    }

    @Transactional
    public void recordResult(UUID deliveryId, SendResult result) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Delivery Not Found: " + deliveryId));

        Subscription subscription = delivery.getSubscription();

        if (result.succeeded()){
            delivery.markSucceeded();
            subscription.recordSuccess();
        }
        else {
            int attemptsAfterThis = delivery.getAttemptCount() + 1;
            subscription.recordFailure(CIRCUIT_THRESHOLD,CIRCUIT_OPEN_FOR);
            if (backoffPolicy.shouldGiveUp(attemptsAfterThis))
                delivery.markDead();
            else {
                Instant next = Instant.now().plus(backoffPolicy.delayAfter(attemptsAfterThis));
                delivery.markFailed(next);
            }
        }

        int attemptNumber = (int) deliveryAttemptRepository.countByDeliveryId(deliveryId) + 1;

        deliveryAttemptRepository.save(new
                DeliveryAttempt(
                delivery,
                attemptNumber,
                result.attemptedAt(),
                result.durationMs(),
                result.responseStatus(),
                result.errorMessage()
        ));
    }
}