package com.github.webhook;

import com.github.webhook.dto.response.EventResponse;
import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.DeliveryStatus;
import com.github.webhook.repository.DeliveryRepository;
import com.github.webhook.service.WebhookSigner;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "webhook.dispatch=db"
)
@Import(TestcontainersConfiguration.class)
class DeliveryFlowIntegrationTest {

    record ReceivedWebhook(String id, String timestamp, String signature, String body) {}

    private static HttpServer receiver;
    private static final List<ReceivedWebhook> received = new CopyOnWriteArrayList<>();

    @LocalServerPort
    private int port;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private WebhookSigner signer;

    @BeforeAll
    static void startFakeSubscriber() throws IOException {
        receiver = HttpServer.create(new InetSocketAddress(0), 0);
        receiver.createContext("/hook", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new ReceivedWebhook(
                    exchange.getRequestHeaders().getFirst("Webhook-Id"),
                    exchange.getRequestHeaders().getFirst("Webhook-Timestamp"),
                    exchange.getRequestHeaders().getFirst("Webhook-Signature"),
                    body
            ));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        receiver.start();
    }

    @AfterAll
    static void stopFakeSubscriber() {
        receiver.stop(0);
    }

    private RestClient api() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void eventIsDeliveredWithAValidSignature() throws InterruptedException {
        String secret = "integration-secret-123456";
        String hookUrl = "http://localhost:" + receiver.getAddress().getPort() + "/hook";

        api().post().uri("/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "tenantId", "it-tenant",
                        "url", hookUrl,
                        "secret", secret,
                        "eventTypes", List.of("order.created")))
                .retrieve()
                .toBodilessEntity();

        EventResponse event = api().post().uri("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "it-1")
                .body(Map.of(
                        "tenantId", "it-tenant",
                        "eventType", "order.created",
                        "payload", Map.of("orderId", 1)))
                .retrieve()
                .body(EventResponse.class);

        Delivery delivery = waitForStatus(event.id(), DeliveryStatus.SUCCEEDED, Duration.ofSeconds(20));

        assertThat(received).hasSize(1);
        ReceivedWebhook webhook = received.getFirst();
        assertThat(webhook.id()).isEqualTo(delivery.getId().toString());

        String expectedSignature = signer.sign(
                secret, webhook.id(), Long.parseLong(webhook.timestamp()), webhook.body());
        assertThat(webhook.signature()).isEqualTo(expectedSignature);
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameEvent() {
        Map<String, Object> body = Map.of(
                "tenantId", "it-idempotency",
                "eventType", "order.created",
                "payload", Map.of("orderId", 2));

        EventResponse first = postEvent("it-2", body);
        EventResponse second = postEvent("it-2", body);

        assertThat(second.id()).isEqualTo(first.id());
    }

    private EventResponse postEvent(String idempotencyKey, Map<String, Object> body) {
        return api().post().uri("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .retrieve()
                .body(EventResponse.class);
    }

    private Delivery waitForStatus(UUID eventId, DeliveryStatus expected, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            List<Delivery> deliveries = deliveryRepository.findByEventId(eventId);
            if (!deliveries.isEmpty() && deliveries.getFirst().getStatus() == expected) {
                return deliveries.getFirst();
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Delivery for event " + eventId + " did not reach " + expected);
    }
}