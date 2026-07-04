package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.dto.QueueDepthResponse;
import com.example.asyncjobs.model.JobStatus;
import com.example.asyncjobs.repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class QueueDepthService {

    private static final Logger log = LoggerFactory.getLogger(QueueDepthService.class);

    private final JobRepository jobRepository;
    private final AppProperties appProperties;
    private final MeterRegistry meterRegistry;
    private final RestClient restClient;

    public QueueDepthService(JobRepository jobRepository,
                             AppProperties appProperties,
                             MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.appProperties = appProperties;
        this.meterRegistry = meterRegistry;
        this.restClient = RestClient.create();
    }

    public QueueDepthResponse getQueueDepth() {
        long queued = jobRepository.countByStatus(JobStatus.QUEUED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long retryScheduled = jobRepository.countByStatus(JobStatus.RETRY_SCHEDULED);
        long succeeded = jobRepository.countByStatus(JobStatus.SUCCEEDED);
        long deadLettered = jobRepository.countByStatus(JobStatus.DEAD_LETTERED);
        long cancelled = jobRepository.countByStatus(JobStatus.CANCELLED);

        meterRegistry.gauge("queue.depth.db", queued + running + retryScheduled);

        Long rabbitReady = null;
        Long rabbitUnacked = null;
        boolean rabbitAvailable = false;

        String managementUrl = appProperties.rabbitmq().managementUrl();
        if (managementUrl != null && !managementUrl.isBlank()) {
            try {
                String queueName = appProperties.rabbitmq().executionQueue();
                String vhost = encodeVhost("/");
                String url = managementUrl.replaceAll("/$", "") + "/api/queues/" + vhost + "/" + queueName;

                JsonNode response = restClient.get()
                        .uri(url)
                        .header("Authorization", basicAuthHeader())
                        .retrieve()
                        .body(JsonNode.class);

                if (response != null) {
                    rabbitReady = response.path("messages_ready").asLong();
                    rabbitUnacked = response.path("messages_unacknowledged").asLong();
                    rabbitAvailable = true;
                    meterRegistry.gauge("queue.depth.rabbit.ready", rabbitReady);
                }
            } catch (Exception e) {
                log.warn("Unable to fetch RabbitMQ management metrics: {}", e.getMessage());
            }
        }

        return new QueueDepthResponse(
                queued, running, retryScheduled, succeeded, deadLettered, cancelled,
                rabbitReady, rabbitUnacked, rabbitAvailable
        );
    }

    private String basicAuthHeader() {
        String credentials = appProperties.rabbitmq().managementUrl().contains("@")
                ? ""
                : System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest") + ":"
                + System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");
        if (credentials.isEmpty()) {
            credentials = System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest") + ":"
                    + System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");
        }
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeVhost(String vhost) {
        return java.net.URLEncoder.encode(vhost, StandardCharsets.UTF_8);
    }
}
