package com.swe.catalogservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            SELECT e
            FROM OutboxEvent e
            WHERE e.publishedAt IS NULL
              AND (e.claimedUntil IS NULL OR e.claimedUntil < :now)
            ORDER BY e.occurredAt ASC
            """)
    List<OutboxEvent> findAvailableEvents(@Param("now") OffsetDateTime now);

    @Modifying
    @Query("""
            UPDATE OutboxEvent e
            SET e.claimedUntil = :claimedUntil
            WHERE e.id = :id
              AND e.publishedAt IS NULL
              AND (e.claimedUntil IS NULL OR e.claimedUntil < :now)
            """)
    int claimEvent(@Param("id") UUID id, @Param("now") OffsetDateTime now, @Param("claimedUntil") OffsetDateTime claimedUntil);
}
