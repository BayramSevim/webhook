package com.github.webhook.service;

import com.github.webhook.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "webhook.dispatch", havingValue = "kafka")
public class KafkaDeliveryListener {
    private static final Logger log = LoggerFactory.getLogger(KafkaDeliveryListener.class);
    private final DeliveryStateService deliveryStateService;
    private final DeliveryProcessor deliveryProcessor;

    public KafkaDeliveryListener(DeliveryStateService deliveryStateService, DeliveryProcessor deliveryProcessor) {
        this.deliveryStateService = deliveryStateService;
        this.deliveryProcessor = deliveryProcessor;
    }

    @KafkaListener(topics = KafkaConfig.DELIVERIES_TOPIC, groupId = "delivery-workers", concurrency = "3")
    public void onMessage(String deliveryId) {
        UUID id = UUID.fromString(deliveryId);
        deliveryStateService.loadJob(id).ifPresentOrElse(
                deliveryProcessor::process,
                () -> log.info("Skipping delivery {} (not in SENDING)", id));
    }
}