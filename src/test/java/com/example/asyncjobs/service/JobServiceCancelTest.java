package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.exception.NotFoundException;
import com.example.asyncjobs.model.Job;
import com.example.asyncjobs.model.JobStatus;
import com.example.asyncjobs.repository.JobRepository;
import com.example.asyncjobs.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceCancelTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private DrainService drainService;

    private JobService jobService;

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
        jobService = new JobService(jobRepository, outboxEventRepository, outboxService, drainService,
                new ObjectMapper(), properties, new SimpleMeterRegistry());
    }

    @Test
    void cancelQueuedJobSucceeds() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job();
        job.setId(jobId);
        job.setStatus(JobStatus.QUEUED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(outboxService.createLifecycleEvent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.example.asyncjobs.model.OutboxEvent());

        var response = jobService.cancelJob(jobId);
        assertEquals(JobStatus.CANCELLED, response.status());
    }

    @Test
    void cancelRetryScheduledJobSucceeds() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job();
        job.setId(jobId);
        job.setStatus(JobStatus.RETRY_SCHEDULED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(outboxService.createLifecycleEvent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.example.asyncjobs.model.OutboxEvent());

        var response = jobService.cancelJob(jobId);
        assertEquals(JobStatus.CANCELLED, response.status());
    }

    @Test
    void cancelRunningJobRejected() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job();
        job.setId(jobId);
        job.setStatus(JobStatus.RUNNING);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalStateException.class, () -> jobService.cancelJob(jobId));
    }

    @Test
    void cancelSucceededJobRejected() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job();
        job.setId(jobId);
        job.setStatus(JobStatus.SUCCEEDED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalStateException.class, () -> jobService.cancelJob(jobId));
    }

    @Test
    void cancelDeadLetteredJobRejected() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job();
        job.setId(jobId);
        job.setStatus(JobStatus.DEAD_LETTERED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalStateException.class, () -> jobService.cancelJob(jobId));
    }

    @Test
    void cancelUnknownJobThrowsNotFound() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> jobService.cancelJob(jobId));
    }
}
