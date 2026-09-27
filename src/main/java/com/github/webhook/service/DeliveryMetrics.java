package com.github.webhook.service;

import com.github.webhook.entity.DeliveryStatus;
import com.github.webhook.repository.DeliveryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeliveryMetrics {

    public DeliveryMetrics(MeterRegistry registry, DeliveryRepository deliveryRepository) {
        Gauge.builder("webhook.deliveries.backlog", deliveryRepository,
                        repo -> repo.countByStatusIn(List.of(
                                DeliveryStatus.PENDING, DeliveryStatus.FAILED, DeliveryStatus.SENDING)))
                .description("Deliveries waiting to be sent or retried")
                .register(registry);
    }
}