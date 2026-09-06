package com.swe.inventoryservice.repository;

import com.swe.inventoryservice.entity.ProcessedEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessedEventRepositoryTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("should save and verify existence of processed event for idempotency")
    void shouldSaveAndCheckExistenceOfProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        ProcessedEvent event = ProcessedEvent.builder()
                .eventId(eventId)
                .processedAt(OffsetDateTime.now())
                .build();

        assertThat(processedEventRepository.existsById(eventId)).isFalse();

        processedEventRepository.saveAndFlush(event);
        entityManager.clear();

        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }
}
