package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.model.OutboxEvent;
import com.example.asyncjobs.model.OutboxStatus;
import com.example.asyncjobs.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final ObjectProvider<OutboxDispatchService> self;

    public OutboxDispatchService(OutboxEventRepository outboxEventRepository,
                                 EventPublisher eventPublisher,
                                 AppProperties appProperties,
                                 MeterRegistry meterRegistry,
                                 ObjectProvider<OutboxDispatchService> self) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.appProperties = appProperties;
        this.meterRegistry = meterRegistry;
        this.self = self;
    }

    public void dispatchBatch(String dispatcherId) {
        List<OutboxEvent> events = self.getObject().findEventsToPublish();
        meterRegistry.gauge("outbox.pending", outboxEventRepository.countByStatus(OutboxStatus.PENDING));

        for (OutboxEvent event : events) {
            self.getObject().publishEvent(dispatcherId, event.getId());
        }
    }

    @Transactional
    public List<OutboxEvent> findEventsToPublish() {
        Instant now = Instant.now();
        List<OutboxEvent> events = outboxEventRepository.claimPendingEvents(
                now, appProperties.outbox().batchSize());
        if (events.isEmpty()) {
            events = outboxEventRepository.findPublishableEvents(
                    now, PageRequest.of(0, appProperties.outbox().batchSize()));
        }
        return events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishEvent(String dispatcherId, UUID eventId) {
        OutboxEvent fresh = outboxEventRepository.findById(eventId).orElse(null);
        if (fresh == null || fresh.getStatus() == OutboxStatus.PUBLISHED) {
            return;
        }
        if (fresh.getPublishAfter().isAfter(Instant.now())) {
            return;
        }

        Instant lockedUntil = Instant.now().plusSeconds(appProperties.outbox().lockSeconds());
        outboxEventRepository.lockEvent(fresh.getId(), dispatcherId, lockedUntil);
        fresh = outboxEventRepository.findById(eventId).orElseThrow();

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
