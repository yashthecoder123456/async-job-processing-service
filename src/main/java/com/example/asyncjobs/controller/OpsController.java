package com.example.asyncjobs.controller;

import com.example.asyncjobs.dto.DrainResponse;
import com.example.asyncjobs.service.DrainService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops")
@ConditionalOnProperty(name = "app.api-enabled", havingValue = "true")
public class OpsController {

    private final DrainService drainService;

    public OpsController(DrainService drainService) {
        this.drainService = drainService;
    }

    @PostMapping("/drain")
    public DrainResponse drain() {
        return drainService.drain();
    }

    @PostMapping("/resume")
    public DrainResponse resume() {
        return drainService.resume();
    }
}
