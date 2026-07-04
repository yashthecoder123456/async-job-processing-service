package com.example.asyncjobs.repository;

import com.example.asyncjobs.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.asyncjobs.model.OutboxStatus;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    long countByStatus(OutboxStatus status);

    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status IN (com.example.asyncjobs.model.OutboxStatus.PENDING, com.example.asyncjobs.model.OutboxStatus.FAILED)
              AND e.publishAfter <= :now
              AND (e.lockedUntil IS NULL OR e.lockedUntil < :now)
            ORDER BY e.createdAt
            """)
    List<OutboxEvent> findPublishableEvents(@Param("now") Instant now, Pageable pageable);

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status IN ('PENDING', 'FAILED')
              AND publish_after <= :now
              AND (locked_until IS NULL OR locked_until < :now)
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimPendingEvents(@Param("now") Instant now,
                                         @Param("batchSize") int batchSize);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE outbox_events
            SET locked_by = :lockedBy,
                locked_until = :lockedUntil,
                status = 'IN_PROGRESS'
            WHERE id = :id
            """, nativeQuery = true)
    int lockEvent(@Param("id") UUID id,
                  @Param("lockedBy") String lockedBy,
                  @Param("lockedUntil") Instant lockedUntil);
}
