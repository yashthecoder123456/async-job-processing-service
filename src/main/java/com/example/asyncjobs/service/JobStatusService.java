package com.example.asyncjobs.service;

import com.example.asyncjobs.dto.JobStatusResponse;
import com.example.asyncjobs.exception.NotFoundException;
import com.example.asyncjobs.model.Job;
import com.example.asyncjobs.repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JobStatusService {

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public JobStatusService(JobRepository jobRepository, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    public JobStatusResponse getJobStatus(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found: " + jobId));

        JsonNode resultPayload = null;
        if (job.getResultPayload() != null) {
            try {
                resultPayload = objectMapper.readTree(job.getResultPayload());
            } catch (Exception e) {
                throw new IllegalStateException("Invalid result payload stored for job " + jobId, e);
            }
        }

        return new JobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getAttemptCount(),
                job.getMaxRetries(),
                job.getPriority(),
                job.getLastError(),
                resultPayload,
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt()
        );
    }
}
