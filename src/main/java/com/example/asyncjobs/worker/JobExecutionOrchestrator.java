package com.example.asyncjobs.worker;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.model.Job;
import com.example.asyncjobs.model.JobAttempt;
import com.example.asyncjobs.model.JobStatus;
import com.example.asyncjobs.repository.JobRepository;
import com.example.asyncjobs.service.DrainService;
import com.example.asyncjobs.service.JobExecutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes the worker-side lifecycle after a message is pulled from RabbitMQ:
 * load job → claim → attempt → handler → persist result → ack/nack decision.
 */
@Service
@ConditionalOnProperty(name = "app.worker-enabled", havingValue = "true")
public class JobExecutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionOrchestrator.class);

    private final JobRepository jobRepository;
    private final JobExecutionService jobExecutionService;
    private final DrainService drainService;
    private final JobHandler jobHandler;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final ExecutorService jobHandlerExecutor;
    private final MeterRegistry meterRegistry;
    private final String workerId;

    public JobExecutionOrchestrator(JobRepository jobRepository,
                                    JobExecutionService jobExecutionService,
                                    DrainService drainService,
                                    JobHandler jobHandler,
                                    ObjectMapper objectMapper,
                                    AppProperties appProperties,
                                    ExecutorService jobHandlerExecutor,
                                    MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.jobExecutionService = jobExecutionService;
        this.drainService = drainService;
        this.jobHandler = jobHandler;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.jobHandlerExecutor = jobHandlerExecutor;
        this.meterRegistry = meterRegistry;
        this.workerId = appProperties.worker().idPrefix() + "-" + UUID.randomUUID();
    }

    public void processExecutionMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        if (!drainService.isWorkersEnabled()) {
            log.info("Workers drained, requeueing execution message");
            channel.basicNack(deliveryTag, false, true);
            return;
        }

        ExecutionMessage executionMessage;
        try {
            executionMessage = objectMapper.readValue(message.getBody(), ExecutionMessage.class);
        } catch (Exception e) {
            log.error("Invalid execution queue payload, rejecting to DLQ");
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        UUID jobId = UUID.fromString(executionMessage.jobId());
        MDC.put("jobId", jobId.toString());
        MDC.put("correlationId", jobId.toString());
        MDC.put("workerId", workerId);

        try {
            log.info("Processing execution message jobId={} attempt={} priority={}",
                    jobId, executionMessage.attemptNumber(), executionMessage.priority());

            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.warn("Job {} not found, acking poison message", jobId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (job.getStatus().isTerminal() || job.getStatus() == JobStatus.CANCELLED) {
                log.info("Skipping job {} in status {}", jobId, job.getStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (!jobExecutionService.tryClaim(jobId, workerId, appProperties.worker().leaseSeconds())) {
                log.info("Could not claim job {}, acking duplicate/stale message", jobId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            job = jobRepository.findById(jobId).orElseThrow();
            int attemptNumber = job.getAttemptCount() + 1;

            JobAttempt attempt = jobExecutionService.startAttempt(
                    jobId, workerId, attemptNumber, appProperties.worker().leaseSeconds());

            job = jobRepository.findById(jobId).orElseThrow();
            String payloadForHandler = enrichPayloadWithAttempt(job.getPayload(), attemptNumber);

            Timer.Sample sample = Timer.start(meterRegistry);
            JobExecutionResult result = executeWithTimeout(payloadForHandler, job.getTimeoutSeconds());
            sample.stop(meterRegistry.timer("worker.execution.duration"));

            jobExecutionService.completeAttempt(job, attempt, result);
            log.info("Job {} finished with status {}", jobId, jobRepository.findById(jobId).map(Job::getStatus).orElse(null));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Infrastructure failure processing job {}", jobId, e);
            channel.basicNack(deliveryTag, false, true);
        } finally {
            MDC.remove("jobId");
            MDC.remove("correlationId");
            MDC.remove("workerId");
        }
    }

    public String workerId() {
        return workerId;
    }

    private JobExecutionResult executeWithTimeout(String payloadJson, int timeoutSeconds) throws Exception {
        Future<JobExecutionResult> future = jobHandlerExecutor.submit(
                () -> jobHandler.handle(payloadJson, timeoutSeconds));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return JobExecutionResult.timeout();
        } catch (Exception e) {
            return JobExecutionResult.failure(e.getMessage(), true);
        }
    }

    private String enrichPayloadWithAttempt(String payloadJson, int attemptNumber) throws Exception {
        JsonNode node = objectMapper.readTree(payloadJson);
        if (node.isObject()) {
            ((ObjectNode) node).put("currentAttempt", attemptNumber);
            return objectMapper.writeValueAsString(node);
        }
        return payloadJson;
    }
}
