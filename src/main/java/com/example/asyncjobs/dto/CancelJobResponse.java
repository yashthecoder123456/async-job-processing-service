package com.example.asyncjobs.dto;

import com.example.asyncjobs.model.JobStatus;

import java.util.UUID;

public record CancelJobResponse(UUID jobId, JobStatus status) {
}
