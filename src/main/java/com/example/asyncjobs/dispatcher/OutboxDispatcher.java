package com.example.asyncjobs.dispatcher;

import com.example.asyncjobs.service.DrainService;
import com.example.asyncjobs.service.OutboxDispatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OutboxDispatcher {

    private final OutboxDispatchService outboxDispatchService;
    private final DrainService drainService;
    private final com.example.asyncjobs.config.AppProperties appProperties;
    private final String dispatcherId;

    public OutboxDispatcher(OutboxDispatchService outboxDispatchService,
                            DrainService drainService,
                            com.example.asyncjobs.config.AppProperties appProperties) {
        this.outboxDispatchService = outboxDispatchService;
        this.drainService = drainService;
        this.appProperties = appProperties;
        this.dispatcherId = "dispatcher-" + UUID.randomUUID();
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    public void dispatch() {
        if (!appProperties.outboxDispatcherEnabled() || !drainService.isDispatcherEnabled()) {
            return;
        }
        outboxDispatchService.dispatchBatch(dispatcherId);
    }
}
