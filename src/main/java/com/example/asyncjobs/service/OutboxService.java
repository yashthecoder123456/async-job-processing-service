package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.model.OutboxEvent;
import com.example.asyncjobs.model.OutboxEventType;
import com.example.asyncjobs.model.OutboxStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public OutboxService(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public OutboxEvent createExecutionRequestedEvent(UUID jobId, int attemptNumber, int priority, Instant publishAfter) {
        return buildEvent(jobId, OutboxEventType.JOB_EXECUTION_REQUESTED, "rabbitmq",
                Map.of(
                        "jobId", jobId.toString(),
                        "attemptNumber", attemptNumber,
                        "priority", priority
                ), publishAfter);
    }

    public OutboxEvent createLifecycleEvent(UUID jobId, OutboxEventType eventType, Map<String, Object> payload) {
        return buildEvent(jobId, eventType, "lifecycle", payload, Instant.now());
    }

    private OutboxEvent buildEvent(UUID aggregateId, OutboxEventType eventType, String destination,
                                   Map<String, Object> payload, Instant publishAfter) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setDestination(destination);
        event.setPublishAfter(publishAfter);
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(Instant.now());
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
        return event;
    }

    public String executionQueueName() {
        return appProperties.rabbitmq().executionQueue();
    }
}
