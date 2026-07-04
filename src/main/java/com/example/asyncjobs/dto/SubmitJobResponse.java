package com.example.asyncjobs.dto;

import com.example.asyncjobs.model.JobStatus;

import java.util.UUID;

public record SubmitJobResponse(UUID jobId, JobStatus status) {
}
