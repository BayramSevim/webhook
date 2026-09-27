package com.github.webhook.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebhookSignerTest {
    private static final String SECRET = "test-secret-1234567890";
    private static final String WEBHOOK_ID = "3f2c9a1e-0000-4000-8000-000000000001";
    private static final long TIMESTAMP = 1700000000L;
    private static final String BODY = "{\"orderId\": 12345}";

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void producesKnownSignature() {
        String signature = signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY);

        assertThat(signature).isEqualTo("v1,IaxyZEQ1xIAz5I7YOgjEhTqW4SjZHlJzKuCWXXoRc+s=");
    }

    @Test
    void sameInputGivesSameSignature() {
        assertThat(signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY))
                .isEqualTo(signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY));
    }

    @Test
    void differentTimestampChangesSignature() {
        assertThat(signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP + 1, BODY))
                .isNotEqualTo(signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY));
    }

    @Test
    void differentSecretChangesSignature() {
        assertThat(signer.sign("another-secret-1234567", WEBHOOK_ID, TIMESTAMP, BODY))
                .isNotEqualTo(signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY));
    }

    @Test
    void whitespaceInBodyChangesSignature() {
        String compactBody = "{\"orderId\":12345}";

        assertThat(signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, compactBody))
                .isNotEqualTo(signer.sign(SECRET, WEBHOOK_ID, TIMESTAMP, BODY));
    }
}
