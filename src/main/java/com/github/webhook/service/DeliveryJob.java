package com.github.webhook.service;


import java.util.UUID;

public record DeliveryJob(
        UUID deliveryId,
        UUID subscriptionId,
        String url,
        String secret,
        String eventType,
        String payload
) {}