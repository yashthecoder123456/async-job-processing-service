package com.example.asyncjobs.integration;

import com.example.asyncjobs.config.RabbitConfig;
import com.example.asyncjobs.dto.DrainResponse;
import com.example.asyncjobs.dto.JobStatusResponse;
import com.example.asyncjobs.dto.QueueDepthResponse;
import com.example.asyncjobs.dto.SubmitJobRequest;
import com.example.asyncjobs.dto.SubmitJobResponse;
import com.example.asyncjobs.model.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AsyncJobIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("asyncjobs")
            .withUsername("asyncjobs")
            .withPassword("asyncjobs");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
        registry.add("app.rabbitmq.management-url",
                () -> "http://" + rabbit.getAdminUsername() + ":" + rabbit.getAdminPassword()
                        + "@" + rabbit.getHost() + ":" + rabbit.getHttpPort());
        registry.add("app.api-enabled", () -> "true");
        registry.add("app.worker-enabled", () -> "true");
        registry.add("app.outbox-dispatcher-enabled", () -> "true");
        registry.add("app.retry.backoff-base-ms", () -> "200");
        registry.add("app.retry.backoff-max-ms", () -> "1000");
        registry.add("app.retry.backoff-jitter-ms", () -> "50");
        registry.add("app.outbox.poll-interval-ms", () -> "200");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void resumeQueue() {
        restTemplate.postForEntity("/api/v1/ops/resume", null, Object.class);
    }

    @Test
    void submitJobReturnsImmediatelyAndCompletes() {
        SubmitJobResponse submitted = submitJob(successPayload(), 5, 3, 10, null);
        assertThat(submitted.status()).isEqualTo(JobStatus.QUEUED);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JobStatusResponse status = getStatus(submitted.jobId());
            assertThat(status.status()).isEqualTo(JobStatus.SUCCEEDED);
            assertThat(status.resultPayload()).isNotNull();
        });
    }

    @Test
    void failUntilAttemptRetriesThenSucceeds() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "failUntilAttempt");
        payload.put("succeedOnAttempt", 3);

        SubmitJobResponse submitted = submitJob(payload, 5, 3, 10, null);

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            JobStatusResponse status = getStatus(submitted.jobId());
            assertThat(status.status()).isEqualTo(JobStatus.SUCCEEDED);
            assertThat(status.attemptCount()).isGreaterThanOrEqualTo(3);
        });
    }

    @Test
    void retriesExhaustedMovesToDeadLetter() {
        ObjectNode payload = objectMapper.createObjectNode().put("type", "fail");
        SubmitJobResponse submitted = submitJob(payload, 5, 1, 10, null);

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            JobStatusResponse status = getStatus(submitted.jobId());
            assertThat(status.status()).isEqualTo(JobStatus.DEAD_LETTERED);
        });
    }

    @Test
    void timeoutCausesDeadLetterWhenRetriesExhausted() {
        ObjectNode payload = objectMapper.createObjectNode().put("type", "timeout");
        SubmitJobResponse submitted = submitJob(payload, 5, 1, 1, null);

        await().atMost(Duration.ofSeconds(90)).untilAsserted(() -> {
            JobStatusResponse status = getStatus(submitted.jobId());
            assertThat(status.status()).isEqualTo(JobStatus.DEAD_LETTERED);
        });
    }

    @Test
    void cancelQueuedJobPreventsExecution() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "sleep");
        payload.put("sleepMs", 5000);
        SubmitJobResponse submitted = submitJob(payload, 5, 0, 10, null);

        restTemplate.postForEntity("/api/v1/jobs/{id}/cancel", null, Object.class, submitted.jobId());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JobStatusResponse status = getStatus(submitted.jobId());
            assertThat(status.status()).isEqualTo(JobStatus.CANCELLED);
        });
    }

    @Test
    void idempotencyKeyReturnsSameJobId() {
        ObjectNode payload = successPayload();
        SubmitJobResponse first = submitJob(payload, 5, 3, 10, "idem-123");
        SubmitJobResponse second = submitJob(payload, 5, 3, 10, "idem-123");
        assertThat(second.jobId()).isEqualTo(first.jobId());
    }

    @Test
    void queueDepthEndpointWorksWithRabbitMetrics() {
        submitJob(successPayload(), 5, 3, 10, null);
        ResponseEntity<QueueDepthResponse> response = restTemplate.getForEntity("/api/v1/queue/depth", QueueDepthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rabbitAvailable()).isTrue();
    }

    @Test
    void drainBlocksSubmissionAndResumeReEnables() {
        ResponseEntity<DrainResponse> drain = restTemplate.postForEntity("/api/v1/ops/drain", null, DrainResponse.class);
        assertThat(drain.getBody().submissionsEnabled()).isFalse();

        SubmitJobRequest request = new SubmitJobRequest(successPayload(), 5, 3, 10, null);
        ResponseEntity<String> blocked = restTemplate.postForEntity("/api/v1/jobs", request, String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<DrainResponse> resume = restTemplate.postForEntity("/api/v1/ops/resume", null, DrainResponse.class);
        assertThat(resume.getBody().submissionsEnabled()).isTrue();

        ResponseEntity<SubmitJobResponse> accepted = restTemplate.postForEntity("/api/v1/jobs", request, SubmitJobResponse.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void duplicateRabbitMqMessageDoesNotReExecuteCompletedJob() throws Exception {
        SubmitJobResponse submitted = submitJob(successPayload(), 5, 3, 10, null);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(getStatus(submitted.jobId()).status()).isEqualTo(JobStatus.SUCCEEDED);
        });

        JobStatusResponse before = getStatus(submitted.jobId());
        int attemptsBefore = before.attemptCount();

        Map<String, Object> duplicate = new HashMap<>();
        duplicate.put("jobId", submitted.jobId().toString());
        duplicate.put("attemptNumber", 1);
        duplicate.put("priority", 5);
        duplicate.put("publishedAt", Instant.now().toString());
        rabbitTemplate.convertAndSend(RabbitConfig.EXECUTION_EXCHANGE, RabbitConfig.EXECUTION_ROUTING_KEY, duplicate);

        Thread.sleep(3000);

        JobStatusResponse after = getStatus(submitted.jobId());
        assertThat(after.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(after.attemptCount()).isEqualTo(attemptsBefore);
    }

    @Test
    void actuatorHealthAndMetricsAvailable() {
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");

        ResponseEntity<String> metrics = restTemplate.getForEntity("/actuator/metrics/jobs.submitted", String.class);
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ObjectNode successPayload() {
        return objectMapper.createObjectNode().put("type", "success");
    }

    private SubmitJobResponse submitJob(ObjectNode payload, int priority, int maxRetries, int timeout, String idempotencyKey) {
        SubmitJobRequest request = new SubmitJobRequest(payload, priority, maxRetries, timeout, idempotencyKey);
        ResponseEntity<SubmitJobResponse> response = restTemplate.postForEntity("/api/v1/jobs", request, SubmitJobResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return response.getBody();
    }

    private JobStatusResponse getStatus(UUID jobId) {
        ResponseEntity<JobStatusResponse> response = restTemplate.getForEntity("/api/v1/jobs/{id}", JobStatusResponse.class, jobId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
