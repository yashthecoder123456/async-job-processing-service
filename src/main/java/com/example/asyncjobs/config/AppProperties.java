package com.example.asyncjobs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String role,
        boolean apiEnabled,
        boolean workerEnabled,
        boolean outboxDispatcherEnabled,
        WorkerProperties worker,
        OutboxProperties outbox,
        RetryProperties retry,
        boolean drainSubmissionsWhenEnabled,
        RabbitProperties rabbitmq,
        ValidationProperties validation
) {
    public record WorkerProperties(int concurrency, long pollIntervalMs, String idPrefix, int leaseSeconds) {
    }

    public record OutboxProperties(int batchSize, long pollIntervalMs, int lockSeconds) {
    }

    public record RetryProperties(long backoffBaseMs, long backoffMaxMs, long backoffJitterMs) {
    }

    public record RabbitProperties(String executionQueue, String executionDlq, int maxPriority, String managementUrl) {
    }

    public record ValidationProperties(int maxPayloadBytes) {
    }
}
