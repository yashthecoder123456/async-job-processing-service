package com.example.asyncjobs.config;

import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerListenerBootstrap {

    @Bean
    ApplicationRunner startWorkerListeners(AppProperties appProperties,
                                         RabbitListenerEndpointRegistry registry) {
        return args -> {
            if (!appProperties.workerEnabled()) {
                return;
            }
            registry.getListenerContainers().forEach(container -> {
                if (!container.isRunning()) {
                    container.start();
                }
            });
        };
    }
}
