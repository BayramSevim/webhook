package com.github.webhook.service;


import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.DeliveryAttempt;
import com.github.webhook.entity.DeliveryStatus;
import com.github.webhook.entity.Subscription;
import com.github.webhook.mapper.DeliveryMapper;
import com.github.webhook.repository.DeliveryAttemptRepository;
import com.github.webhook.repository.DeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final DeliveryMapper deliveryMapper;
    private final MeterRegistry meterRegistry;

    private static final int CIRCUIT_THRESHOLD = 5;
    private static final Duration CIRCUIT_OPEN_FOR = Duration.ofMinutes(1);

    public DeliveryStateService(DeliveryRepository deliveryRepository, DeliveryAttemptRepository deliveryAttemptRepository, BackoffPolicy backoffPolicy, DeliveryMapper deliveryMapper, MeterRegistry meterRegistry) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.backoffPolicy = backoffPolicy;
        this.deliveryMapper = deliveryMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public List<DeliveryJob> claimDue(int batchSize) {
        return deliveryRepository.findDue(batchSize)
                .stream()
                .map(delivery -> {
                    delivery.markSending(Instant.now().plus(LEASE));
                    return deliveryMapper.toJob(delivery);
                }).toList();
    }

    @Transactional
    public void recordResult(UUID deliveryId, SendResult result) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Delivery Not Found: " + deliveryId));

        Subscription subscription = delivery.getSubscription();

        String outcome;
        if (result.succeeded()) {
            delivery.markSucceeded();
            subscription.recordSuccess();
            outcome = "succeeded";
        } else {
            int attemptsAfterThis = delivery.getAttemptCount() + 1;
            subscription.recordFailure(CIRCUIT_THRESHOLD, CIRCUIT_OPEN_FOR);
            if (backoffPolicy.shouldGiveUp(attemptsAfterThis)) {
                delivery.markDead();
                outcome = "dead";
            } else {
                delivery.markFailed(Instant.now().plus(backoffPolicy.delayAfter(attemptsAfterThis)));
                outcome = "failed";
            }
        }

        meterRegistry.counter("webhook.deliveries", "outcome", outcome).increment();
        meterRegistry.timer("webhook.delivery.duration", "outcome", outcome)
                .record(Duration.ofMillis(result.durationMs()));

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

    @Transactional(readOnly = true)
    public Optional<DeliveryJob> loadJob(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.SENDING)
                .map(deliveryMapper::toJob);
    }
}