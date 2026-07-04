package com.example.asyncjobs.repository;

import com.example.asyncjobs.model.DeadLetterJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeadLetterJobRepository extends JpaRepository<DeadLetterJob, UUID> {

    Optional<DeadLetterJob> findByJobId(UUID jobId);
}
