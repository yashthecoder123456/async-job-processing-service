package com.example.asyncjobs.controller;

import com.example.asyncjobs.dto.QueueDepthResponse;
import com.example.asyncjobs.service.QueueDepthService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
@ConditionalOnProperty(name = "app.api-enabled", havingValue = "true")
public class QueueController {

    private final QueueDepthService queueDepthService;

    public QueueController(QueueDepthService queueDepthService) {
        this.queueDepthService = queueDepthService;
    }

    @GetMapping("/depth")
    public QueueDepthResponse getQueueDepth() {
        return queueDepthService.getQueueDepth();
    }
}
