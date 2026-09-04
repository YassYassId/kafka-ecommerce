package com.swe.ordersservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.publishedAt = CURRENT_TIMESTAMP
        WHERE e.id = :id
        """)
    int markAsPublished(@Param("id") UUID id);

    @Query(value = """
        SELECT *
        FROM outbox_events
        WHERE published_at IS NULL
          AND (
              claimed_until IS NULL
              OR claimed_until < CURRENT_TIMESTAMP
          )
        ORDER BY created_at
        LIMIT 100
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findClaimableEvents();

    @Modifying
    @Query("""
        UPDATE OutboxEvent e
        SET e.retryCount = e.retryCount + 1,
            e.lastError = :error
        WHERE e.id = :id
        """)
    int recordFailure(
            @Param("id") UUID id,
            @Param("error") String error
    );
}
