package com.example.asyncjobs.service;

import com.example.asyncjobs.config.AppProperties;
import com.example.asyncjobs.config.RabbitConfig;
import com.example.asyncjobs.model.OutboxEvent;
import com.example.asyncjobs.model.OutboxEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class RabbitMqEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final LifecycleEventPublisher lifecycleEventPublisher;

    public RabbitMqEventPublisher(RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper,
                                  AppProperties appProperties,
                                  LifecycleEventPublisher lifecycleEventPublisher) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
    }

    @Override
    public void publish(OutboxEvent event) {
        if (event.getEventType() == OutboxEventType.JOB_EXECUTION_REQUESTED) {
            publishExecutionEvent(event);
        } else {
            lifecycleEventPublisher.publish(event);
        }
    }

    private void publishExecutionEvent(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            Map<String, Object> message = new HashMap<>();
            message.put("jobId", payload.get("jobId").asText());
            message.put("attemptNumber", payload.get("attemptNumber").asInt());
            message.put("priority", payload.get("priority").asInt());
            message.put("publishedAt", Instant.now().toString());

            byte[] body = objectMapper.writeValueAsBytes(message);
            int priority = payload.get("priority").asInt();

            Message amqpMessage = MessageBuilder.withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setPriority(priority)
                    .build();

            rabbitTemplate.send(
                    RabbitConfig.EXECUTION_EXCHANGE,
                    RabbitConfig.EXECUTION_ROUTING_KEY,
                    amqpMessage
            );
            log.info("Published execution event for job {}", payload.get("jobId").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish execution event to RabbitMQ", e);
        }
    }
}
