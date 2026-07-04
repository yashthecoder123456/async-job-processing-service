package com.example.asyncjobs.dto;

public record QueueDepthResponse(
        long queued,
        long running,
        long retryScheduled,
        long succeeded,
        long deadLettered,
        long cancelled,
        Long rabbitReady,
        Long rabbitUnacked,
        boolean rabbitAvailable
) {
}
