package com.example.asyncjobs.dto;

import com.example.asyncjobs.model.JobStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record JobStatusResponse(
        UUID jobId,
        JobStatus status,
        int attemptCount,
        int maxRetries,
        int priority,
        String lastError,
        JsonNode resultPayload,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
