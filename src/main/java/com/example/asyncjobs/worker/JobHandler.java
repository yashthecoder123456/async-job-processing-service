package com.example.asyncjobs.worker;

public interface JobHandler {

    JobExecutionResult handle(String payloadJson, int timeoutSeconds) throws Exception;
}
