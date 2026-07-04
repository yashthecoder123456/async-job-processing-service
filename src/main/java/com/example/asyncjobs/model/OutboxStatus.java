package com.example.asyncjobs.model;

public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED,
    FAILED
}
