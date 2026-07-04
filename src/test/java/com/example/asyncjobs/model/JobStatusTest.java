package com.example.asyncjobs.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobStatusTest {

    @Test
    void terminalStatuses() {
        assertTrue(JobStatus.SUCCEEDED.isTerminal());
        assertTrue(JobStatus.DEAD_LETTERED.isTerminal());
        assertTrue(JobStatus.CANCELLED.isTerminal());
        assertFalse(JobStatus.QUEUED.isTerminal());
        assertFalse(JobStatus.RUNNING.isTerminal());
    }

    @Test
    void claimableStatuses() {
        assertTrue(JobStatus.QUEUED.isClaimable());
        assertTrue(JobStatus.RETRY_SCHEDULED.isClaimable());
        assertFalse(JobStatus.RUNNING.isClaimable());
    }
}
