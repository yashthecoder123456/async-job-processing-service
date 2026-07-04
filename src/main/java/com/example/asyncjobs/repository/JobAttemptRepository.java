package com.example.asyncjobs.repository;

import com.example.asyncjobs.model.JobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobAttemptRepository extends JpaRepository<JobAttempt, UUID> {
}
