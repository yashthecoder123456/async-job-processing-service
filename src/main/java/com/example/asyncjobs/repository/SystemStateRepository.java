package com.example.asyncjobs.repository;

import com.example.asyncjobs.model.SystemState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemStateRepository extends JpaRepository<SystemState, String> {
}
