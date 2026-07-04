package com.example.asyncjobs.service;

import com.example.asyncjobs.model.OutboxEvent;

public interface EventPublisher {

    void publish(OutboxEvent event);
}
