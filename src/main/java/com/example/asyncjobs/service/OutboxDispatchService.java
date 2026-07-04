package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.model.OutboxEvent;
import com.example.asyncjobs.model.OutboxStatus;
import com.example.asyncjobs.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxDispatchService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;
    private final AppProperties appProperties;
    private final MeterRegistry meterRegistry;

    public OutboxDispatchService(OutboxEventRepository outboxEventRepository,
                                 EventPublisher eventPublisher,
                                 AppProperties appProperties,
                                 MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.appProperties = appProperties;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void dispatchBatch(String dispatcherId) {
        Instant now = Instant.now();
        List<OutboxEvent> events = outboxEventRepository.claimPendingEvents(now, appProperties.outbox().batchSize());
        meterRegistry.gauge("outbox.pending", outboxEventRepository.countByStatus(OutboxStatus.PENDING));

        for (OutboxEvent event : events) {
            Instant lockedUntil = now.plusSeconds(appProperties.outbox().lockSeconds());
            outboxEventRepository.lockEvent(event.getId(), dispatcherId, lockedUntil);
            publishEvent(event.getId());
        }
    }

    void publishEvent(UUID eventId) {
        OutboxEvent fresh = outboxEventRepository.findById(eventId).orElseThrow();
        try {
            eventPublisher.publish(fresh);
            fresh.setStatus(OutboxStatus.PUBLISHED);
            fresh.setPublishedAt(Instant.now());
            fresh.setLockedBy(null);
            fresh.setLockedUntil(null);
            outboxEventRepository.save(fresh);
            meterRegistry.counter("outbox.published").increment();
        } catch (Exception e) {
            log.error("Failed to publish outbox event {}", fresh.getId(), e);
            fresh.setStatus(OutboxStatus.FAILED);
            fresh.setRetryCount(fresh.getRetryCount() + 1);
            fresh.setLastError(e.getMessage());
            fresh.setLockedBy(null);
            fresh.setLockedUntil(null);
            outboxEventRepository.save(fresh);
            meterRegistry.counter("outbox.failed").increment();
        }
    }
}
