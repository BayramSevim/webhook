package com.github.webhook.mapper;

import com.github.webhook.entity.Delivery;
import com.github.webhook.service.DeliveryJob;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMapper {
    public DeliveryJob toJob(Delivery delivery) {
        return new DeliveryJob(
                delivery.getId(),
                delivery.getSubscription().getId(),
                delivery.getSubscription().getUrl(),
                delivery.getSubscription().getSecret(),
                delivery.getEvent().getEventType(),
                delivery.getEvent().getPayload()
        );
    }
}
