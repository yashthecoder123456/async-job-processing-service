package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryServiceTest {

    private RetryService retryService;

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
        retryService = new RetryService(properties);
    }

    @Test
    void calculateBackoffIncreasesWithAttempts() {
        long first = retryService.calculateBackoffMs(1);
        long second = retryService.calculateBackoffMs(2);
        assertTrue(second >= first);
    }

    @Test
    void calculateBackoffRespectsMax() {
        long backoff = retryService.calculateBackoffMs(20);
        assertTrue(backoff <= 60000 + 500);
    }

    @Test
    void hasRetriesRemaining() {
        assertTrue(retryService.hasRetriesRemaining(1, 3));
        assertTrue(retryService.hasRetriesRemaining(3, 3));
        assertFalse(retryService.hasRetriesRemaining(4, 3));
    }
}
