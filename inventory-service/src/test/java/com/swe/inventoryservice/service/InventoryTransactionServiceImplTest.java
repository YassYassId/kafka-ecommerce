package com.swe.inventoryservice.service;

import com.swe.inventoryservice.entity.InventoryItem;
import com.swe.inventoryservice.entity.ProcessedEvent;
import com.swe.inventoryservice.event.InventoryRejectedEvent;
import com.swe.inventoryservice.event.InventoryReservedEvent;
import com.swe.inventoryservice.event.OrderCreatedEvent;
import com.swe.inventoryservice.event.OrderCreatedItem;
import com.swe.inventoryservice.outbox.OutboxEvent;
import com.swe.inventoryservice.outbox.OutboxEventRepository;
import com.swe.inventoryservice.repository.InventoryItemRepository;
import com.swe.inventoryservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryTransactionServiceImplTest {

    @Mock
    private InventoryItemRepository repository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private InventoryTransactionServiceImpl inventoryTransactionService;

    @Nested
    @DisplayName("process")
    class ProcessTests {

        @Test
        @DisplayName("should skip processing when event has already been processed (idempotency)")
        void shouldSkipWhenEventAlreadyProcessed() {
            UUID eventId = UUID.randomUUID();
            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    List.of(new OrderCreatedItem(UUID.randomUUID(), 2)),
                    Instant.now(),
                    1
            );

            when(processedEventRepository.existsById(eventId)).thenReturn(true);

            inventoryTransactionService.process(event);

            verify(processedEventRepository).existsById(eventId);
            verifyNoInteractions(repository);
            verifyNoInteractions(outboxEventRepository);
            verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        }

        @Test
        @DisplayName("should successfully deduct available quantity, increase reserved quantity, and create InventoryReserved outbox event for single item")
        void shouldProcessOrderSuccessfullyForSingleItem() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId,
                    orderId,
                    UUID.randomUUID(),
                    List.of(new OrderCreatedItem(productId, 3)),
                    Instant.now(),
                    1
            );

            InventoryItem inventoryItem = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .productId(productId)
                    .availableQuantity(10)
                    .reservedQuantity(2)
                    .version(0L)
                    .build();

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(repository.findByProductId(productId)).thenReturn(Optional.of(inventoryItem));

            inventoryTransactionService.process(event);

            assertThat(inventoryItem.getAvailableQuantity()).isEqualTo(7);
            assertThat(inventoryItem.getReservedQuantity()).isEqualTo(5);

            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(outboxCaptor.capture());

            OutboxEvent savedOutbox = outboxCaptor.getValue();
            assertThat(savedOutbox.getAggregateId()).isEqualTo(orderId);
            assertThat(savedOutbox.getAggregateType()).isEqualTo("Order");
            assertThat(savedOutbox.getEventType()).isEqualTo("InventoryReserved");
            assertThat(savedOutbox.getEventVersion()).isEqualTo(1);
            assertThat(savedOutbox.getPayload()).contains(orderId.toString());

            ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
            verify(processedEventRepository).save(eventCaptor.capture());

            ProcessedEvent savedEvent = eventCaptor.getValue();
            assertThat(savedEvent.getEventId()).isEqualTo(eventId);
            assertThat(savedEvent.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("should successfully process order with multiple items and create InventoryReserved outbox event")
        void shouldProcessOrderWithMultipleItems() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID product1Id = UUID.randomUUID();
            UUID product2Id = UUID.randomUUID();

            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId,
                    orderId,
                    UUID.randomUUID(),
                    List.of(
                            new OrderCreatedItem(product1Id, 2),
                            new OrderCreatedItem(product2Id, 4)
                    ),
                    Instant.now(),
                    1
            );

            InventoryItem item1 = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .productId(product1Id)
                    .availableQuantity(5)
                    .reservedQuantity(0)
                    .version(0L)
                    .build();

            InventoryItem item2 = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .productId(product2Id)
                    .availableQuantity(10)
                    .reservedQuantity(1)
                    .version(0L)
                    .build();

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(repository.findByProductId(product1Id)).thenReturn(Optional.of(item1));
            when(repository.findByProductId(product2Id)).thenReturn(Optional.of(item2));

            inventoryTransactionService.process(event);

            assertThat(item1.getAvailableQuantity()).isEqualTo(3);
            assertThat(item1.getReservedQuantity()).isEqualTo(2);

            assertThat(item2.getAvailableQuantity()).isEqualTo(6);
            assertThat(item2.getReservedQuantity()).isEqualTo(5);

            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(outboxCaptor.capture());
            assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("InventoryReserved");

            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }

        @Test
        @DisplayName("should create InventoryRejected outbox event when product does not exist in inventory")
        void shouldRejectWhenProductNotFound() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID missingProductId = UUID.randomUUID();

            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId,
                    orderId,
                    UUID.randomUUID(),
                    List.of(new OrderCreatedItem(missingProductId, 1)),
                    Instant.now(),
                    1
            );

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(repository.findByProductId(missingProductId)).thenReturn(Optional.empty());

            inventoryTransactionService.process(event);

            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(outboxCaptor.capture());

            OutboxEvent savedOutbox = outboxCaptor.getValue();
            assertThat(savedOutbox.getAggregateId()).isEqualTo(orderId);
            assertThat(savedOutbox.getEventType()).isEqualTo("InventoryRejected");
            assertThat(savedOutbox.getPayload()).contains("PRODUCT_NOT_FOUND");
            assertThat(savedOutbox.getPayload()).contains(missingProductId.toString());

            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }

        @Test
        @DisplayName("should create InventoryRejected outbox event when requested quantity exceeds available stock")
        void shouldRejectWhenInsufficientInventory() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId,
                    orderId,
                    UUID.randomUUID(),
                    List.of(new OrderCreatedItem(productId, 15)),
                    Instant.now(),
                    1
            );

            InventoryItem inventoryItem = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .productId(productId)
                    .availableQuantity(10)
                    .reservedQuantity(0)
                    .version(0L)
                    .build();

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(repository.findByProductId(productId)).thenReturn(Optional.of(inventoryItem));

            inventoryTransactionService.process(event);

            // Verify quantities were not changed
            assertThat(inventoryItem.getAvailableQuantity()).isEqualTo(10);
            assertThat(inventoryItem.getReservedQuantity()).isEqualTo(0);

            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(outboxCaptor.capture());

            OutboxEvent savedOutbox = outboxCaptor.getValue();
            assertThat(savedOutbox.getAggregateId()).isEqualTo(orderId);
            assertThat(savedOutbox.getEventType()).isEqualTo("InventoryRejected");
            assertThat(savedOutbox.getPayload()).contains("INSUFFICIENT_STOCK");

            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }
    }
}
