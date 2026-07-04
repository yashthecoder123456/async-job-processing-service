package com.example.asyncjobs.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleJobHandlerTest {

    private SampleJobHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new SampleJobHandler(objectMapper);
    }

    @Test
    void successPayload() throws Exception {
        String payload = "{\"type\":\"success\"}";
        JobExecutionResult result = handler.handle(payload, 5);
        assertTrue(result.success());
    }

    @Test
    void failPayloadIsNotRetryable() throws Exception {
        String payload = "{\"type\":\"fail\"}";
        JobExecutionResult result = handler.handle(payload, 5);
        assertFalse(result.success());
        assertFalse(result.retryable());
    }

    @Test
    void failUntilAttemptRetriesUntilThreshold() throws Exception {
        String payload = "{\"type\":\"failUntilAttempt\",\"succeedOnAttempt\":3,\"currentAttempt\":2}";
        JobExecutionResult result = handler.handle(payload, 5);
        assertFalse(result.success());
        assertTrue(result.retryable());
    }

    @Test
    void unknownPayloadTypeIsNotRetryable() throws Exception {
        JobExecutionResult result = handler.handle("{\"type\":\"unknown\"}", 5);
        assertFalse(result.success());
        assertFalse(result.retryable());
    }

    @Test
    void timeoutPayloadBlocksLongerThanTimeoutSeconds() throws Exception {
        String payload = "{\"type\":\"timeout\"}";
        long start = System.currentTimeMillis();
        JobExecutionResult result = handler.handle(payload, 1);
        assertTrue(result.success());
        assertTrue(System.currentTimeMillis() - start >= 1000);
    }

    @Test
    void sleepPayloadCompletesAfterDelay() throws Exception {
        long start = System.currentTimeMillis();
        String payload = "{\"type\":\"sleep\",\"sleepMs\":50}";
        JobExecutionResult result = handler.handle(payload, 5);
        assertTrue(result.success());
        assertTrue(System.currentTimeMillis() - start >= 50);
    }
}
