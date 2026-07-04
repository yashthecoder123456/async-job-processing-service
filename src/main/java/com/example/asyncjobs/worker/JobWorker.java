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
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "app.worker-enabled", havingValue = "true")
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobRepository jobRepository;
    private final JobExecutionService jobExecutionService;
    private final DrainService drainService;
    private final JobHandler jobHandler;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final ExecutorService jobHandlerExecutor;
    private final MeterRegistry meterRegistry;
    private final String workerId;

    public JobWorker(JobRepository jobRepository,
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

    @RabbitListener(queues = "#{@executionQueue.name}")
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        if (!drainService.isWorkersEnabled()) {
            log.info("Workers disabled, requeueing message");
            channel.basicNack(deliveryTag, false, true);
            return;
        }

        JsonNode body;
        try {
            body = objectMapper.readTree(message.getBody());
        } catch (Exception e) {
            log.error("Invalid message payload, sending to DLQ via nack without requeue");
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        UUID jobId = UUID.fromString(body.get("jobId").asText());
        MDC.put("jobId", jobId.toString());

        try {
            Job job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.warn("Job {} not found, acking poison message", jobId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (job.getStatus().isTerminal() || job.getStatus() == JobStatus.CANCELLED) {
                log.info("Skipping job {} in terminal/cancelled status {}", jobId, job.getStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            Instant now = Instant.now();
            int claimed = jobRepository.claimJob(
                    jobId,
                    workerId,
                    now.plusSeconds(appProperties.worker().leaseSeconds()),
                    now
            );

            if (claimed == 0) {
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
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Infrastructure failure processing job {}", jobId, e);
            channel.basicNack(deliveryTag, false, true);
        } finally {
            MDC.remove("jobId");
        }
    }

    private JobExecutionResult executeWithTimeout(String payloadJson, int timeoutSeconds) throws Exception {
        Future<JobExecutionResult> future = jobHandlerExecutor.submit(() -> jobHandler.handle(payloadJson, timeoutSeconds));
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
