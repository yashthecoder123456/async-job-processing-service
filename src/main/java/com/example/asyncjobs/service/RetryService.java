package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class RetryService {

    private final AppProperties appProperties;

    public RetryService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public long calculateBackoffMs(int attemptNumber) {
        long base = appProperties.retry().backoffBaseMs();
        long max = appProperties.retry().backoffMaxMs();
        long jitter = appProperties.retry().backoffJitterMs();

        long exponential = base * (1L << Math.max(0, attemptNumber - 1));
        long capped = Math.min(exponential, max);
        long jitterValue = jitter > 0 ? ThreadLocalRandom.current().nextLong(0, jitter + 1) : 0;
        return capped + jitterValue;
    }

    public boolean hasRetriesRemaining(int attemptCount, int maxRetries) {
        return attemptCount <= maxRetries;
    }
}
