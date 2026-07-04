package com.example.asyncjobs.service;

import com.example.asyncjobs.model.AttemptStatus;
import com.example.asyncjobs.model.DeadLetterJob;
import com.example.asyncjobs.model.Job;
import com.example.asyncjobs.model.JobAttempt;
import com.example.asyncjobs.model.JobStatus;
import com.example.asyncjobs.model.OutboxEvent;
import com.example.asyncjobs.model.OutboxEventType;
import com.example.asyncjobs.repository.DeadLetterJobRepository;
import com.example.asyncjobs.repository.JobAttemptRepository;
import com.example.asyncjobs.repository.JobRepository;
import com.example.asyncjobs.repository.OutboxEventRepository;
import com.example.asyncjobs.worker.JobExecutionResult;
import jakarta.persistence.EntityManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class JobExecutionService {

    private final JobRepository jobRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final DeadLetterJobRepository deadLetterJobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxService outboxService;
    private final RetryService retryService;
    private final MeterRegistry meterRegistry;
    private final EntityManager entityManager;

    public JobExecutionService(JobRepository jobRepository,
                               JobAttemptRepository jobAttemptRepository,
                               DeadLetterJobRepository deadLetterJobRepository,
                               OutboxEventRepository outboxEventRepository,
                               OutboxService outboxService,
                               RetryService retryService,
                               MeterRegistry meterRegistry,
                               EntityManager entityManager) {
        this.jobRepository = jobRepository;
        this.jobAttemptRepository = jobAttemptRepository;
        this.deadLetterJobRepository = deadLetterJobRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxService = outboxService;
        this.retryService = retryService;
        this.meterRegistry = meterRegistry;
        this.entityManager = entityManager;
    }

    @Transactional
    public boolean tryClaim(UUID jobId, String workerId, int leaseSeconds) {
        Instant now = Instant.now();
        if (jobRepository.claimJob(jobId, workerId, now.plusSeconds(leaseSeconds), now) == 0) {
            return false;
        }
        entityManager.clear();
        return true;
    }

    @Transactional
    public JobAttempt startAttempt(UUID jobId, String workerId, int attemptNumber, int leaseSeconds) {
        Instant now = Instant.now();
        JobAttempt attempt = new JobAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setJobId(jobId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setWorkerId(workerId);
        attempt.setStatus(AttemptStatus.RUNNING);
        attempt.setStartedAt(now);
        jobAttemptRepository.save(attempt);

        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setAttemptCount(attemptNumber);
        job.setUpdatedAt(now);
        jobRepository.save(job);

        OutboxEvent started = outboxService.createLifecycleEvent(
                jobId, OutboxEventType.JOB_STARTED,
                Map.of("jobId", jobId.toString(), "attemptNumber", attemptNumber));
        outboxEventRepository.save(started);
        return attempt;
    }

    @Transactional
    public void completeAttempt(Job job, JobAttempt attempt, JobExecutionResult result) {
        Instant now = Instant.now();
        attempt.setFinishedAt(now);
        attempt.setDurationMs(now.toEpochMilli() - attempt.getStartedAt().toEpochMilli());

        if (result.success()) {
            attempt.setStatus(AttemptStatus.SUCCEEDED);
            job.setStatus(JobStatus.SUCCEEDED);
            job.setResultPayload(result.resultPayload());
            job.setLastError(null);
            job.setCompletedAt(now);
            job.setLockedBy(null);
            job.setLockedUntil(null);
            job.setUpdatedAt(now);
            jobAttemptRepository.save(attempt);
            jobRepository.save(job);

            OutboxEvent succeeded = outboxService.createLifecycleEvent(
                    job.getId(), OutboxEventType.JOB_SUCCEEDED,
                    Map.of("jobId", job.getId().toString()));
            outboxEventRepository.save(succeeded);
            meterRegistry.counter("jobs.succeeded").increment();
            return;
        }

        if (result.timedOut()) {
            attempt.setStatus(AttemptStatus.TIMEOUT);
            attempt.setErrorMessage("TIMEOUT");
        } else {
            attempt.setStatus(AttemptStatus.FAILED);
            attempt.setErrorMessage(result.errorMessage());
        }
        jobAttemptRepository.save(attempt);

        if (result.retryable() && retryService.hasRetriesRemaining(job.getAttemptCount(), job.getMaxRetries())) {
            scheduleRetry(job, result.errorMessage() != null ? result.errorMessage() : "TIMEOUT");
            meterRegistry.counter("jobs.retry_scheduled").increment();
            return;
        }

        deadLetter(job, result.errorMessage() != null ? result.errorMessage() : "TIMEOUT");
        meterRegistry.counter("jobs.dead_lettered").increment();
        meterRegistry.counter("jobs.failed").increment();
    }

    private void scheduleRetry(Job job, String errorMessage) {
        Instant now = Instant.now();
        long backoffMs = retryService.calculateBackoffMs(job.getAttemptCount());
        Instant publishAfter = now.plusMillis(backoffMs);

        job.setStatus(JobStatus.RETRY_SCHEDULED);
        job.setLastError(errorMessage);
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setUpdatedAt(now);
        jobRepository.save(job);

        OutboxEvent retryEvent = outboxService.createExecutionRequestedEvent(
                job.getId(), job.getAttemptCount() + 1, job.getPriority(), publishAfter);
        outboxEventRepository.save(retryEvent);

        OutboxEvent lifecycle = outboxService.createLifecycleEvent(
                job.getId(), OutboxEventType.JOB_FAILED_RETRYING,
                Map.of("jobId", job.getId().toString(), "publishAfter", publishAfter.toString()));
        outboxEventRepository.save(lifecycle);
    }

    private void deadLetter(Job job, String errorMessage) {
        Instant now = Instant.now();
        job.setStatus(JobStatus.DEAD_LETTERED);
        job.setLastError(errorMessage);
        job.setCompletedAt(now);
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setUpdatedAt(now);
        jobRepository.save(job);

        DeadLetterJob deadLetterJob = new DeadLetterJob();
        deadLetterJob.setId(UUID.randomUUID());
        deadLetterJob.setJobId(job.getId());
        deadLetterJob.setPayload(job.getPayload());
        deadLetterJob.setFinalError(errorMessage);
        deadLetterJob.setAttemptCount(job.getAttemptCount());
        deadLetterJob.setDeadLetteredAt(now);
        deadLetterJobRepository.save(deadLetterJob);

        OutboxEvent lifecycle = outboxService.createLifecycleEvent(
                job.getId(), OutboxEventType.JOB_DEAD_LETTERED,
                Map.of("jobId", job.getId().toString(), "error", errorMessage));
        outboxEventRepository.save(lifecycle);
    }
}
