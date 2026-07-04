package com.example.asyncjobs.dto;

public record DrainResponse(boolean submissionsEnabled, boolean dispatcherEnabled, boolean workersEnabled) {
}
