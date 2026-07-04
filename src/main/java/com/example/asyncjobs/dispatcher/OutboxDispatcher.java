package com.example.asyncjobs.dispatcher;

import com.example.asyncjobs.service.DrainService;
import com.example.asyncjobs.service.OutboxDispatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.outbox-dispatcher-enabled", havingValue = "true")
public class OutboxDispatcher {

    private final OutboxDispatchService outboxDispatchService;
    private final DrainService drainService;
    private final String dispatcherId;

    public OutboxDispatcher(OutboxDispatchService outboxDispatchService,
                            DrainService drainService) {
        this.outboxDispatchService = outboxDispatchService;
        this.drainService = drainService;
        this.dispatcherId = "dispatcher-" + UUID.randomUUID();
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    public void dispatch() {
        if (!drainService.isDispatcherEnabled()) {
            return;
        }
        outboxDispatchService.dispatchBatch(dispatcherId);
    }
}
