package com.github.webhook.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record CreateSubscriptionRequest(
        @NotBlank
        @Size(max = 100)
        String tenantId,

        @NotBlank
        @URL(regexp = "^https?://.*$")
        String url,

        @NotBlank
        @Size(min = 16)
        String secret,

        @NotEmpty
        List<@NotBlank String> eventTypes
) {
}