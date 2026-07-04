package com.example.asyncjobs.service;

import com.example.asyncjobs.model.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LifecycleEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LifecycleEventPublisher.class);

    public void publish(OutboxEvent event) {
        log.info("lifecycle_event type={} aggregateId={} payload={}",
                event.getEventType(), event.getAggregateId(), event.getPayload());
    }
}
