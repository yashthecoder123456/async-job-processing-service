package com.example.asyncjobs.worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Message contract for {@code job.execution.queue}.
 * Published by {@link com.example.asyncjobs.service.RabbitMqEventPublisher}
 * and consumed by {@link JobExecutionQueueConsumer}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionMessage(
        String jobId,
        int attemptNumber,
        int priority,
        String publishedAt
) {
}
