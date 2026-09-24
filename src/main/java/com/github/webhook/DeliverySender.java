package com.github.webhook;

import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.DeliveryAttempt;
import com.github.webhook.entity.Event;
import com.github.webhook.entity.Subscription;
import com.github.webhook.repository.DeliveryAttemptRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Component
public class DeliverySender {

    private final RestClient restClient;
    private final DeliveryAttemptRepository attemptRepository;


    public DeliverySender(RestClient restClient, DeliveryAttemptRepository attemptRepository) {
        this.restClient = restClient;
        this.attemptRepository = attemptRepository;
    }

    public void send(Delivery delivery) {
        Subscription subscription = delivery.getSubscription();
        Event event = delivery.getEvent();

        Instant attemptedAt = Instant.now();
        long start = System.nanoTime();

        Integer responseStatus = null;
        String errorMessage = null;

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(subscription.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Webhook-Id", delivery.getId().toString())
                    .header("Webhook-Event", event.getEventType())
                    .body(event.getPayload())
                    .retrieve()
                    .toBodilessEntity();

            responseStatus = response.getStatusCode().value();
            delivery.markSucceeded();

        } catch (RestClientResponseException e) {
            responseStatus = e.getStatusCode().value();
            errorMessage = "HTTP " + responseStatus;
            delivery.markFailed();

        } catch (RestClientException e) {
            errorMessage = e.getMessage();
            delivery.markFailed();
        }

        int durationMs = (int) ((System.nanoTime() - start) / 1_000_000);

        attemptRepository.save(new DeliveryAttempt(
                delivery,
                delivery.getAttemptCount(),
                attemptedAt,
                durationMs,
                responseStatus,
                errorMessage
        ));
    }
}