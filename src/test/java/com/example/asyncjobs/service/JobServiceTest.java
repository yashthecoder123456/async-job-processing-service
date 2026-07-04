package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.exception.ValidationException;
import com.example.asyncjobs.dto.SubmitJobRequest;
import com.example.asyncjobs.dto.SubmitJobResponse;
import com.example.asyncjobs.model.Job;
import com.example.asyncjobs.model.JobStatus;
import com.example.asyncjobs.model.OutboxEvent;
import com.example.asyncjobs.model.OutboxEventType;
import com.example.asyncjobs.repository.JobRepository;
import com.example.asyncjobs.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private DrainService drainService;

    private JobService jobService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
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
                objectMapper, properties, new SimpleMeterRegistry());
        when(drainService.isSubmissionsEnabled()).thenReturn(true);
    }

    @Test
    void idempotencyKeyReturnsExistingJob() {
        UUID jobId = UUID.randomUUID();
        Job existing = new Job();
        existing.setId(jobId);
        existing.setStatus(JobStatus.QUEUED);
        when(jobRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        ObjectNode payload = objectMapper.createObjectNode().put("type", "success");
        SubmitJobRequest request = new SubmitJobRequest(payload, 5, 3, 10, "key-1");
        SubmitJobResponse response = jobService.submitJob(request);

        assertEquals(jobId, response.jobId());
        assertEquals(JobStatus.QUEUED, response.status());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void submitJobCreatesJobAndBothOutboxEvents() {
        when(outboxService.createLifecycleEvent(any(), eq(OutboxEventType.JOB_SUBMITTED), any()))
                .thenReturn(new OutboxEvent());
        when(outboxService.createExecutionRequestedEvent(any(), eq(1), eq(5), any()))
                .thenReturn(new OutboxEvent());

        ObjectNode payload = objectMapper.createObjectNode().put("type", "success");
        SubmitJobRequest request = new SubmitJobRequest(payload, 5, 3, 10, null);
        SubmitJobResponse response = jobService.submitJob(request);

        assertEquals(JobStatus.QUEUED, response.status());
        verify(jobRepository).save(any(Job.class));
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
        verify(outboxService).createLifecycleEvent(any(), eq(OutboxEventType.JOB_SUBMITTED), any());
        verify(outboxService).createExecutionRequestedEvent(any(), eq(1), eq(5), any());
    }

    @Test
    void oversizedPayloadRejected() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("data", "x".repeat(70000));
        SubmitJobRequest request = new SubmitJobRequest(payload, 5, 3, 10, null);
        assertThrows(ValidationException.class, () -> jobService.submitJob(request));
        verify(jobRepository, never()).save(any());
    }
}
