package com.example.asyncjobs.config;

import com.example.asyncjobs.worker.JobExecutionOrchestrator;
import com.example.asyncjobs.worker.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs when each runtime role is ready so the end-to-end pipeline is observable at startup.
 *
 * Workflow: POST /jobs → PostgreSQL (jobs + outbox) → OutboxDispatcher → RabbitMQ queue →
 * JobExecutionQueueConsumer → JobExecutionOrchestrator → JobHandler → PostgreSQL status update.
 */
@Component
public class ExecutionPipelineStartup {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPipelineStartup.class);

    private final AppProperties appProperties;
    private final JobHandler jobHandler;
    private final ObjectProvider<JobExecutionOrchestrator> orchestrator;

    public ExecutionPipelineStartup(AppProperties appProperties,
                                    JobHandler jobHandler,
                                    ObjectProvider<JobExecutionOrchestrator> orchestrator) {
        this.appProperties = appProperties;
        this.jobHandler = jobHandler;
        this.orchestrator = orchestrator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logPipelineReady() {
        log.info("Execution pipeline role={} api={} worker={} dispatcher={}",
                appProperties.role(),
                appProperties.apiEnabled(),
                appProperties.workerEnabled(),
                appProperties.outboxDispatcherEnabled());

        if (appProperties.apiEnabled()) {
            log.info("API ready: POST /api/v1/jobs persists jobs + outbox_events (same transaction)");
        }

        if (appProperties.outboxDispatcherEnabled()) {
            log.info("Outbox dispatcher ready: polling outbox → publishing to queue {}",
                    appProperties.rabbitmq().executionQueue());
        }

        if (appProperties.workerEnabled()) {
            log.info(
                    "Worker queue consumer ready: queue={} concurrency={} handler={} workerId={}",
                    appProperties.rabbitmq().executionQueue(),
                    appProperties.worker().concurrency(),
                    jobHandler.getClass().getSimpleName(),
                    orchestrator.getObject().workerId()
            );
        }
    }
}
