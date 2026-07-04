package com.example.asyncjobs.worker;

public record JobExecutionResult(
        boolean success,
        boolean retryable,
        String resultPayload,
        String errorMessage,
        boolean timedOut
) {
    public static JobExecutionResult success(String resultPayload) {
        return new JobExecutionResult(true, false, resultPayload, null, false);
    }

    public static JobExecutionResult failure(String errorMessage, boolean retryable) {
        return new JobExecutionResult(false, retryable, null, errorMessage, false);
    }

    public static JobExecutionResult timeout() {
        return new JobExecutionResult(false, true, null, "TIMEOUT", true);
    }
}
