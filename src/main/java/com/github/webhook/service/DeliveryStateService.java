package com.github.webhook.service;


import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.DeliveryAttempt;
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

    public DeliveryStateService(DeliveryRepository deliveryRepository, DeliveryAttemptRepository deliveryAttemptRepository) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
    }

    @Transactional
    public List<DeliveryJob> claimDue(int batchSize) {
       return  deliveryRepository.findDue(batchSize)
                .stream()
                .map(delivery -> {
                    delivery.markSending(Instant.now().plus(LEASE));
                    return new DeliveryJob(
                            delivery.getId(),
                            delivery.getSubscription().getUrl(),
                            delivery.getEvent().getEventType(),
                            delivery.getEvent().getPayload()
                            );
                }).toList();
    }

    @Transactional
    public void recordResult(UUID deliveryId, SendResult result) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(()-> new IllegalStateException("Delivery Not Found: " + deliveryId));

        if (result.succeeded())
            delivery.markSucceeded();
         else
            delivery.markFailed();

         deliveryAttemptRepository.save(new DeliveryAttempt(
                 delivery,
                 delivery.getAttemptCount(),
                 result.attemptedAt(),
                 result.durationMs(),
                 result.responseStatus(),
                 result.errorMessage()
         ));
    }
}