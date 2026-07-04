package com.example.asyncjobs.model;

public enum JobStatus {
    QUEUED,
    RUNNING,
    RETRY_SCHEDULED,
    SUCCEEDED,
    DEAD_LETTERED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == DEAD_LETTERED || this == CANCELLED;
    }

    public boolean isClaimable() {
        return this == QUEUED || this == RETRY_SCHEDULED;
    }
}
