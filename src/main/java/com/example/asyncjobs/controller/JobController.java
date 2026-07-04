package com.example.asyncjobs.controller;

import com.example.asyncjobs.dto.SubmitJobRequest;
import com.example.asyncjobs.dto.SubmitJobResponse;
import com.example.asyncjobs.service.JobService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@ConditionalOnProperty(name = "app.api-enabled", havingValue = "true")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<SubmitJobResponse> submitJob(@Valid @RequestBody SubmitJobRequest request) {
        SubmitJobResponse response = jobService.submitJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
