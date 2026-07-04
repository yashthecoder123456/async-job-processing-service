package com.example.asyncjobs.controller;

import com.example.asyncjobs.dto.CancelJobResponse;
import com.example.asyncjobs.dto.JobStatusResponse;
import com.example.asyncjobs.service.JobService;
import com.example.asyncjobs.service.JobStatusService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@ConditionalOnProperty(name = "app.api-enabled", havingValue = "true")
public class JobStatusController {

    private final JobStatusService jobStatusService;
    private final JobService jobService;

    public JobStatusController(JobStatusService jobStatusService, JobService jobService) {
        this.jobStatusService = jobStatusService;
        this.jobService = jobService;
    }

    @GetMapping("/{jobId}")
    public JobStatusResponse getJobStatus(@PathVariable UUID jobId) {
        return jobStatusService.getJobStatus(jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public CancelJobResponse cancelJob(@PathVariable UUID jobId) {
        return jobService.cancelJob(jobId);
    }
}
