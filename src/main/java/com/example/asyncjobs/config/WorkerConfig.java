package com.example.asyncjobs.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.worker-enabled", havingValue = "true")
public class WorkerConfig {
}
