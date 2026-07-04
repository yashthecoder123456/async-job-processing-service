package com.example.asyncjobs.service;

import com.example.asyncjobs.dto.DrainResponse;
import com.example.asyncjobs.model.SystemState;
import com.example.asyncjobs.repository.SystemStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DrainService {

    public static final String SUBMISSIONS_ENABLED = "submissions_enabled";
    public static final String DISPATCHER_ENABLED = "dispatcher_enabled";
    public static final String WORKERS_ENABLED = "workers_enabled";

    private final SystemStateRepository systemStateRepository;

    public DrainService(SystemStateRepository systemStateRepository) {
        this.systemStateRepository = systemStateRepository;
    }

    @Transactional
    public DrainResponse drain() {
        setFlag(SUBMISSIONS_ENABLED, false);
        setFlag(DISPATCHER_ENABLED, false);
        setFlag(WORKERS_ENABLED, false);
        return currentState();
    }

    @Transactional
    public DrainResponse resume() {
        setFlag(SUBMISSIONS_ENABLED, true);
        setFlag(DISPATCHER_ENABLED, true);
        setFlag(WORKERS_ENABLED, true);
        return currentState();
    }

    public boolean isSubmissionsEnabled() {
        return getBoolean(SUBMISSIONS_ENABLED, true);
    }

    public boolean isDispatcherEnabled() {
        return getBoolean(DISPATCHER_ENABLED, true);
    }

    public boolean isWorkersEnabled() {
        return getBoolean(WORKERS_ENABLED, true);
    }

    public DrainResponse currentState() {
        return new DrainResponse(
                isSubmissionsEnabled(),
                isDispatcherEnabled(),
                isWorkersEnabled()
        );
    }

    private void setFlag(String key, boolean enabled) {
        SystemState state = systemStateRepository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Missing system state key: " + key));
        state.setValue(Boolean.toString(enabled));
        state.setUpdatedAt(Instant.now());
        systemStateRepository.save(state);
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        return systemStateRepository.findById(key)
                .map(SystemState::getValue)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }
}
