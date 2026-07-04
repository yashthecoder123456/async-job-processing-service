package com.example.asyncjobs.model;

public enum OutboxEventType {
    JOB_EXECUTION_REQUESTED,
    JOB_SUBMITTED,
    JOB_STARTED,
    JOB_SUCCEEDED,
    JOB_FAILED_RETRYING,
    JOB_DEAD_LETTERED,
    JOB_CANCELLED
}
