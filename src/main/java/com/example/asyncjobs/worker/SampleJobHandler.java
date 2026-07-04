package com.example.asyncjobs.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SampleJobHandler implements JobHandler {

    private final ObjectMapper objectMapper;

    public SampleJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JobExecutionResult handle(String payloadJson, int timeoutSeconds) throws Exception {
        JsonNode payload = objectMapper.readTree(payloadJson);
        String type = payload.path("type").asText("success");

        return switch (type) {
            case "success" -> JobExecutionResult.success(
                    objectMapper.writeValueAsString(java.util.Map.of("status", "ok", "type", "success")));
            case "fail" -> JobExecutionResult.failure("Simulated permanent failure", false);
            case "failUntilAttempt" -> {
                int succeedOnAttempt = payload.path("succeedOnAttempt").asInt(3);
                int currentAttempt = payload.path("currentAttempt").asInt(1);
                if (currentAttempt >= succeedOnAttempt) {
                    yield JobExecutionResult.success(objectMapper.writeValueAsString(
                            java.util.Map.of("status", "ok", "attempt", currentAttempt)));
                }
                yield JobExecutionResult.failure("Failing until attempt " + succeedOnAttempt, true);
            }
            case "sleep" -> {
                long sleepMs = payload.path("sleepMs").asLong(100);
                Thread.sleep(sleepMs);
                yield JobExecutionResult.success(objectMapper.writeValueAsString(
                        java.util.Map.of("status", "ok", "sleptMs", sleepMs)));
            }
            case "timeout" -> {
                long blockMs = (timeoutSeconds + 5L) * 1000L;
                Thread.sleep(blockMs);
                yield JobExecutionResult.success(objectMapper.writeValueAsString(
                        java.util.Map.of("status", "unexpected-success-after-timeout")));
            }
            default -> JobExecutionResult.failure("Unknown payload type: " + type, false);
        };
    }
}
