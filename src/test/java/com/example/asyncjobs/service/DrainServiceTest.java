package com.example.asyncjobs.service;

import com.example.asyncjobs.model.SystemState;
import com.example.asyncjobs.repository.SystemStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrainServiceTest {

    @Mock
    private SystemStateRepository systemStateRepository;

    private DrainService drainService;

    @BeforeEach
    void setUp() {
        drainService = new DrainService(systemStateRepository);
    }

    @Test
    void drainDisablesAllFlags() {
        stubAllFlags("true");
        drainService.drain();
        ArgumentCaptor<SystemState> captor = ArgumentCaptor.forClass(SystemState.class);
        verify(systemStateRepository, org.mockito.Mockito.atLeast(3)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(s -> "false".equals(s.getValue())));
    }

    @Test
    void resumeEnablesAllFlags() {
        stubAllFlags("false");
        drainService.resume();
        ArgumentCaptor<SystemState> captor = ArgumentCaptor.forClass(SystemState.class);
        verify(systemStateRepository, org.mockito.Mockito.atLeast(3)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(s -> "true".equals(s.getValue())));
    }

    @Test
    void isSubmissionsEnabledDefaultsTrueWhenMissing() {
        when(systemStateRepository.findById(DrainService.SUBMISSIONS_ENABLED)).thenReturn(Optional.empty());
        assertTrue(drainService.isSubmissionsEnabled());
    }

    private void stubAllFlags(String value) {
        when(systemStateRepository.findById(DrainService.SUBMISSIONS_ENABLED))
                .thenReturn(Optional.of(state(DrainService.SUBMISSIONS_ENABLED, value)));
        when(systemStateRepository.findById(DrainService.DISPATCHER_ENABLED))
                .thenReturn(Optional.of(state(DrainService.DISPATCHER_ENABLED, value)));
        when(systemStateRepository.findById(DrainService.WORKERS_ENABLED))
                .thenReturn(Optional.of(state(DrainService.WORKERS_ENABLED, value)));
    }

    private SystemState state(String key, String value) {
        SystemState state = new SystemState();
        state.setKey(key);
        state.setValue(value);
        state.setUpdatedAt(Instant.now());
        return state;
    }
}
