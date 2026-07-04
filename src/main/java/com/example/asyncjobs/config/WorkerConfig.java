package com.example.asyncjobs.config;

import com.example.asyncjobs.worker.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkerConfig.class);

    @Bean
    ApplicationRunner workerStartupLogger(AppProperties appProperties, JobHandler jobHandler) {
        return args -> {
            if (!appProperties.workerEnabled()) {
                log.info("Worker runtime disabled (APP_ROLE={}, WORKER_ENABLED=false)", appProperties.role());
                return;
            }
            log.info(
                    "Worker pool ready: role={}, rabbitConsumers={}, handlerThreads={}, handler={}, queue={}",
                    appProperties.role(),
                    appProperties.worker().concurrency(),
                    appProperties.worker().concurrency(),
                    jobHandler.getClass().getSimpleName(),
                    appProperties.rabbitmq().executionQueue()
            );
        };
    }
}
