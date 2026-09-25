package com.github.webhook;

import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.DeliveryAttempt;
import com.github.webhook.entity.Event;
import com.github.webhook.entity.Subscription;
import com.github.webhook.repository.DeliveryAttemptRepository;
import com.github.webhook.service.DeliveryJob;
import com.github.webhook.service.SendResult;
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


    public DeliverySender(RestClient restClient ) {
        this.restClient = restClient;
    }

    public SendResult send(DeliveryJob job) {
        long start = System.nanoTime();

        Instant attemptedAt = Instant.now();
        Integer responseStatus = null;
        String errorMessage = null;

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(job.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Webhook-Id", job.deliveryId().toString())
                    .header("Webhook-Event", job.eventType())
                    .body(job.payload())
                    .retrieve()
                    .toBodilessEntity();

            responseStatus = response.getStatusCode().value();


        } catch (RestClientResponseException e) {
            responseStatus = e.getStatusCode().value();
            errorMessage = "HTTP " + responseStatus;
        } catch (RestClientException e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }

        int durationMs = (int) ((System.nanoTime() - start) / 1_000_000);

        return new SendResult(
                attemptedAt,
                responseStatus,
                errorMessage,
                durationMs
        );

    }
}