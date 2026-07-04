package com.example.asyncjobs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_jobs")
public class DeadLetterJob {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "final_error")
    private String finalError;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "dead_lettered_at", nullable = false)
    private Instant deadLetteredAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getFinalError() {
        return finalError;
    }

    public void setFinalError(String finalError) {
        this.finalError = finalError;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getDeadLetteredAt() {
        return deadLetteredAt;
    }

    public void setDeadLetteredAt(Instant deadLetteredAt) {
        this.deadLetteredAt = deadLetteredAt;
    }
}
