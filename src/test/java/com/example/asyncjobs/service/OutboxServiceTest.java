package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.model.OutboxEventType;
import com.example.asyncjobs.model.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxServiceTest {

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "all", true, true, true,
                new AppProperties.WorkerProperties(2, 100, "worker", 60),
                new AppProperties.OutboxProperties(50, 500, 30),
                new AppProperties.RetryProperties(1000, 60000, 500),
                true,
                new AppProperties.RabbitProperties("job.execution.queue", "job.execution.dlq", 10, ""),
                new AppProperties.ValidationProperties(65536)
        );
        outboxService = new OutboxService(new ObjectMapper(), properties);
    }

    @Test
    void createExecutionRequestedEvent() {
        UUID jobId = UUID.randomUUID();
        Instant publishAfter = Instant.now();
        var event = outboxService.createExecutionRequestedEvent(jobId, 2, 7, publishAfter);

        assertEquals(OutboxEventType.JOB_EXECUTION_REQUESTED, event.getEventType());
        assertEquals("rabbitmq", event.getDestination());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(publishAfter, event.getPublishAfter());
        assertTrue(event.getPayload().contains(jobId.toString()));
    }

    @Test
    void createLifecycleEvent() {
        UUID jobId = UUID.randomUUID();
        var event = outboxService.createLifecycleEvent(jobId, OutboxEventType.JOB_SUBMITTED, Map.of("jobId", jobId.toString()));

        assertEquals(OutboxEventType.JOB_SUBMITTED, event.getEventType());
        assertEquals("lifecycle", event.getDestination());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
    }
}
