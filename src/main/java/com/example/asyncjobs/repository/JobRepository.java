package com.example.asyncjobs.repository;

import com.example.asyncjobs.model.Job;
import com.example.asyncjobs.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(JobStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE jobs
            SET status = 'RUNNING',
                locked_by = :workerId,
                locked_until = :lockedUntil,
                updated_at = :now
            WHERE id = :jobId
              AND status IN ('QUEUED', 'RETRY_SCHEDULED')
              AND (locked_until IS NULL OR locked_until < :now)
            """, nativeQuery = true)
    int claimJob(@Param("jobId") UUID jobId,
                 @Param("workerId") String workerId,
                 @Param("lockedUntil") Instant lockedUntil,
                 @Param("now") Instant now);
}
