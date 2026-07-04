package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.dto.SubmitJobRequest;
import com.example.asyncjobs.dto.SubmitJobResponse;
import com.example.asyncjobs.exception.ValidationException;
import com.example.asyncjobs.model.Job;
import com.example.asyncjobs.model.JobStatus;
import com.example.asyncjobs.model.OutboxEvent;
import com.example.asyncjobs.model.OutboxEventType;
import com.example.asyncjobs.repository.JobRepository;
import com.example.asyncjobs.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxService outboxService;
    private final DrainService drainService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final MeterRegistry meterRegistry;

    public JobService(JobRepository jobRepository,
                      OutboxEventRepository outboxEventRepository,
                      OutboxService outboxService,
                      DrainService drainService,
                      ObjectMapper objectMapper,
                      AppProperties appProperties,
                      MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxService = outboxService;
        this.drainService = drainService;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public SubmitJobResponse submitJob(SubmitJobRequest request) {
        if (appProperties.drainSubmissionsWhenEnabled() && !drainService.isSubmissionsEnabled()) {
            throw new IllegalStateException("Job submissions are currently disabled (drain mode)");
        }

        validatePayloadSize(request);

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            return jobRepository.findByIdempotencyKey(request.idempotencyKey())
                    .map(job -> new SubmitJobResponse(job.getId(), job.getStatus()))
                    .orElseGet(() -> createJob(request));
        }

        return createJob(request);
    }

    @Transactional
    public com.example.asyncjobs.dto.CancelJobResponse cancelJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new com.example.asyncjobs.exception.NotFoundException("Job not found: " + jobId));

        if (job.getStatus() == JobStatus.SUCCEEDED || job.getStatus() == JobStatus.DEAD_LETTERED) {
            throw new IllegalStateException("Cannot cancel job in status " + job.getStatus());
        }

        if (job.getStatus() == JobStatus.RUNNING) {
            throw new IllegalStateException("Running job cancellation is best effort and not supported via API once handler started");
        }

        if (job.getStatus() != JobStatus.CANCELLED) {
            if (job.getStatus() != JobStatus.QUEUED && job.getStatus() != JobStatus.RETRY_SCHEDULED) {
                throw new IllegalStateException("Only QUEUED or RETRY_SCHEDULED jobs can be cancelled");
            }
            job.setStatus(JobStatus.CANCELLED);
            job.setUpdatedAt(Instant.now());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            OutboxEvent cancelledEvent = outboxService.createLifecycleEvent(
                    jobId, OutboxEventType.JOB_CANCELLED, Map.of("jobId", jobId.toString()));
            outboxEventRepository.save(cancelledEvent);
        }

        return new com.example.asyncjobs.dto.CancelJobResponse(jobId, job.getStatus());
    }

    private SubmitJobResponse createJob(SubmitJobRequest request) {
        Instant now = Instant.now();
        UUID jobId = UUID.randomUUID();

        Job job = new Job();
        job.setId(jobId);
        try {
            job.setPayload(objectMapper.writeValueAsString(request.payload()));
        } catch (Exception e) {
            throw new ValidationException("Invalid payload JSON");
        }
        job.setStatus(JobStatus.QUEUED);
        job.setPriority(request.priority());
        job.setMaxRetries(request.maxRetries());
        job.setTimeoutSeconds(request.timeoutSeconds());
        job.setAttemptCount(0);
        job.setIdempotencyKey(request.idempotencyKey());
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobRepository.save(job);

        OutboxEvent submitted = outboxService.createLifecycleEvent(
                jobId, OutboxEventType.JOB_SUBMITTED, Map.of("jobId", jobId.toString()));
        OutboxEvent execution = outboxService.createExecutionRequestedEvent(jobId, 1, request.priority(), now);
        outboxEventRepository.save(submitted);
        outboxEventRepository.save(execution);

        meterRegistry.counter("jobs.submitted").increment();
        return new SubmitJobResponse(jobId, JobStatus.QUEUED);
    }

    private void validatePayloadSize(SubmitJobRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request.payload());
            if (payload.getBytes().length > appProperties.validation().maxPayloadBytes()) {
                throw new ValidationException("Payload exceeds maximum allowed size");
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Invalid payload JSON");
        }
    }
}
