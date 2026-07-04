package com.example.asyncjobs.worker;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer for {@code job.execution.queue}.
 * This is the entry point of the async execution workflow after the outbox dispatcher publishes.
 */
@Component
@ConditionalOnProperty(name = "app.worker-enabled", havingValue = "true")
public class JobExecutionQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionQueueConsumer.class);

    private final JobExecutionOrchestrator orchestrator;

    public JobExecutionQueueConsumer(JobExecutionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @RabbitListener(queues = "${app.rabbitmq.execution-queue:job.execution.queue}")
    public void onExecutionMessage(Message message, Channel channel) throws Exception {
        log.debug("Received execution queue message");
        orchestrator.processExecutionMessage(message, channel);
    }
}
