package com.example.asyncjobs.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmitJobRequestValidationTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        objectMapper = new ObjectMapper();
    }

    @Test
    void validRequestPassesValidation() {
        ObjectNode payload = objectMapper.createObjectNode().put("type", "success");
        SubmitJobRequest request = new SubmitJobRequest(payload, 5, 3, 10, "key-1");
        Set<ConstraintViolation<SubmitJobRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void priorityOutOfRangeFails() {
        ObjectNode payload = objectMapper.createObjectNode().put("type", "success");
        SubmitJobRequest request = new SubmitJobRequest(payload, 11, 3, 10, null);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void maxRetriesOutOfRangeFails() {
        ObjectNode payload = objectMapper.createObjectNode().put("type", "success");
        SubmitJobRequest request = new SubmitJobRequest(payload, 5, 11, 10, null);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void timeoutOutOfRangeFails() {
        ObjectNode payload = objectMapper.createObjectNode().put("type", "success");
        SubmitJobRequest request = new SubmitJobRequest(payload, 5, 3, 301, null);
        assertFalse(validator.validate(request).isEmpty());
    }
}
