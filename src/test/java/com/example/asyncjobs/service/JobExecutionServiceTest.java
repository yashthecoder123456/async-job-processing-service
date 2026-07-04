package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.model.AttemptStatus;
import com.example.asyncjobs.model.Job;
import com.example.asyncjobs.model.JobAttempt;
import com.example.asyncjobs.model.JobStatus;
import com.example.asyncjobs.model.OutboxEvent;
import com.example.asyncjobs.repository.DeadLetterJobRepository;
import com.example.asyncjobs.repository.JobAttemptRepository;
import com.example.asyncjobs.repository.JobRepository;
import com.example.asyncjobs.repository.OutboxEventRepository;
import com.example.asyncjobs.worker.JobExecutionResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExecutionServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobAttemptRepository jobAttemptRepository;
    @Mock
    private DeadLetterJobRepository deadLetterJobRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private EntityManager entityManager;

    private JobExecutionService jobExecutionService;
    private RetryService retryService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "all", true, true, true,
                new AppProperties.WorkerProperties(2, 100, "worker", 60),
                new AppProperties.OutboxProperties(50, 500, 30),
                new AppProperties.RetryProperties(1000, 60000, 500),
                true,
                new AppProperties.RabbitProperties("job.execution.queue", "job.execution.dlq", 10, ""),
                new AppProperties.ValidationProperties(65536)
        );
        retryService = new RetryService(properties);
        jobExecutionService = new JobExecutionService(
                jobRepository, jobAttemptRepository, deadLetterJobRepository,
                outboxEventRepository, outboxService, retryService,
                new SimpleMeterRegistry(), entityManager);
        lenient().when(outboxService.createExecutionRequestedEvent(any(), any(Integer.class), any(Integer.class), any()))
                .thenReturn(new OutboxEvent());
        lenient().when(outboxService.createLifecycleEvent(any(), any(), any()))
                .thenReturn(new OutboxEvent());
    }

    @Test
    void timeoutSchedulesRetryWhenAttemptsRemain() {
        Job job = runningJob(1, 3);
        JobAttempt attempt = runningAttempt(job.getId(), 1);

        jobExecutionService.completeAttempt(job, attempt, JobExecutionResult.timeout());

        assertEquals(AttemptStatus.TIMEOUT, attempt.getStatus());
        assertEquals(JobStatus.RETRY_SCHEDULED, job.getStatus());
        verify(deadLetterJobRepository, never()).save(any());
        verify(outboxEventRepository, atLeast(1)).save(any(OutboxEvent.class));
    }

    @Test
    void timeoutDeadLettersWhenRetriesExhausted() {
        Job job = runningJob(3, 2);
        JobAttempt attempt = runningAttempt(job.getId(), 3);

        jobExecutionService.completeAttempt(job, attempt, JobExecutionResult.timeout());

        assertEquals(AttemptStatus.TIMEOUT, attempt.getStatus());
        assertEquals(JobStatus.DEAD_LETTERED, job.getStatus());
        verify(deadLetterJobRepository).save(any());
    }

    private Job runningJob(int attemptCount, int maxRetries) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setStatus(JobStatus.RUNNING);
        job.setAttemptCount(attemptCount);
        job.setMaxRetries(maxRetries);
        job.setPayload("{\"type\":\"timeout\"}");
        job.setPriority(5);
        return job;
    }

    private JobAttempt runningAttempt(UUID jobId, int attemptNumber) {
        JobAttempt attempt = new JobAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setJobId(jobId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setWorkerId("worker-test");
        attempt.setStatus(AttemptStatus.RUNNING);
        attempt.setStartedAt(java.time.Instant.now().minusSeconds(1));
        return attempt;
    }
}
