package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.dto.QueueDepthResponse;
import com.example.asyncjobs.model.JobStatus;
import com.example.asyncjobs.repository.JobRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueDepthServiceTest {

    @Mock
    private JobRepository jobRepository;

    private QueueDepthService queueDepthService;

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
        queueDepthService = new QueueDepthService(jobRepository, properties, new SimpleMeterRegistry());
    }

    @Test
    void returnsDbCountsWhenRabbitManagementUnavailable() {
        when(jobRepository.countByStatus(JobStatus.QUEUED)).thenReturn(2L);
        when(jobRepository.countByStatus(JobStatus.RUNNING)).thenReturn(1L);
        when(jobRepository.countByStatus(JobStatus.RETRY_SCHEDULED)).thenReturn(0L);
        when(jobRepository.countByStatus(JobStatus.SUCCEEDED)).thenReturn(5L);
        when(jobRepository.countByStatus(JobStatus.DEAD_LETTERED)).thenReturn(1L);
        when(jobRepository.countByStatus(JobStatus.CANCELLED)).thenReturn(0L);

        QueueDepthResponse response = queueDepthService.getQueueDepth();

        assertEquals(2, response.queued());
        assertEquals(1, response.running());
        assertEquals(5, response.succeeded());
        assertFalse(response.rabbitAvailable());
        assertNull(response.rabbitReady());
        assertNull(response.rabbitUnacked());
    }
}
