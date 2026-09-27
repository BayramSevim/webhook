package com.github.webhook;
import com.github.webhook.service.DeliveryJob;
import com.github.webhook.service.SendResult;
import com.github.webhook.service.WebhookSigner;
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
    private final WebhookSigner webhookSigner;


    public DeliverySender(RestClient restClient, WebhookSigner webhookSigner) {
        this.restClient = restClient;
        this.webhookSigner = webhookSigner;
    }

    public SendResult send(DeliveryJob job) {
        long start = System.nanoTime();

        Instant attemptedAt = Instant.now();
        Integer responseStatus = null;
        String errorMessage = null;

        long timestamp = Instant.now().getEpochSecond();
        String signature = webhookSigner.sign(
                job.secret(),
                job.deliveryId().toString(),
                timestamp,
                job.payload().toString()
        );

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(job.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Webhook-Id", job.deliveryId().toString())
                    .header("Webhook-Event", job.eventType())
                    .header("Webhook-Timestamp", String.valueOf(timestamp))
                    .header("Webhook-Signature", signature)
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