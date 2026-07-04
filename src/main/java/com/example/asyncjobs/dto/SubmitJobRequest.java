package com.example.asyncjobs.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitJobRequest(
        @NotNull JsonNode payload,
        @Min(1) @Max(10) int priority,
        @Min(0) @Max(10) int maxRetries,
        @Min(1) @Max(300) int timeoutSeconds,
        String idempotencyKey
) {
}
