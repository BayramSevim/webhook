package com.github.webhook.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record CreateEventRequest(
        @NotBlank
        @Size(max = 100)
        String tenantId,

        @NotBlank
        @Size(max = 100)
        String eventType,

        @NotNull
        JsonNode payload
) {
    @AssertTrue(message = "payload must be a JSON object")
    public boolean isPayloadValid() {
        return payload != null && payload.isObject();
    }
}
