package com.swe.inventoryservice.service;

import com.swe.inventoryservice.entity.InventoryItem;
import com.swe.inventoryservice.entity.ProcessedEvent;
import com.swe.inventoryservice.event.ProductCreatedEvent;
import com.swe.inventoryservice.event.ProductRetiredEvent;
import com.swe.inventoryservice.repository.InventoryItemRepository;
import com.swe.inventoryservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogEventServiceImplTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private CatalogEventServiceImpl catalogEventService;

    @Nested
    @DisplayName("handleProductCreatedEvent")
    class HandleProductCreatedEventTests {

        @Test
        @DisplayName("should initialize inventory item with productActive=true and record processed event when new product is created")
        void shouldInitializeInventoryItemWhenNewProductCreated() {
            UUID eventId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            ProductCreatedEvent event = new ProductCreatedEvent(
                    eventId,
                    productId,
                    "SKU-100",
                    "Laptop Pro",
                    "High performance laptop",
                    "Electronics",
                    new BigDecimal("1299.99"),
                    "USD",
                    OffsetDateTime.now(),
                    1
            );

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(inventoryItemRepository.existsByProductId(productId)).thenReturn(false);

            catalogEventService.handleProductCreatedEvent(event);

            ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
            verify(inventoryItemRepository).save(itemCaptor.capture());

            InventoryItem savedItem = itemCaptor.getValue();
            assertThat(savedItem.getProductId()).isEqualTo(productId);
            assertThat(savedItem.getAvailableQuantity()).isZero();
            assertThat(savedItem.getReservedQuantity()).isZero();
            assertThat(savedItem.isProductActive()).isTrue();

            ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
            verify(processedEventRepository).save(eventCaptor.capture());

            ProcessedEvent savedEvent = eventCaptor.getValue();
            assertThat(savedEvent.getEventId()).isEqualTo(eventId);
            assertThat(savedEvent.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("should skip processing when ProductCreated event has already been processed (idempotency)")
        void shouldSkipWhenProductCreatedEventAlreadyProcessed() {
            UUID eventId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            ProductCreatedEvent event = new ProductCreatedEvent(
                    eventId,
                    productId,
                    "SKU-100",
                    "Laptop Pro",
                    "High performance laptop",
                    "Electronics",
                    new BigDecimal("1299.99"),
                    "USD",
                    OffsetDateTime.now(),
                    1
            );

            when(processedEventRepository.existsById(eventId)).thenReturn(true);

            catalogEventService.handleProductCreatedEvent(event);

            verify(processedEventRepository).existsById(eventId);
            verifyNoInteractions(inventoryItemRepository);
            verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        }

        @Test
        @DisplayName("should not create duplicate inventory item when inventory already exists for product ID but record processed event")
        void shouldNotCreateDuplicateInventoryWhenProductAlreadyExists() {
            UUID eventId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            ProductCreatedEvent event = new ProductCreatedEvent(
                    eventId,
                    productId,
                    "SKU-100",
                    "Laptop Pro",
                    "High performance laptop",
                    "Electronics",
                    new BigDecimal("1299.99"),
                    "USD",
                    OffsetDateTime.now(),
                    1
            );

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(inventoryItemRepository.existsByProductId(productId)).thenReturn(true);

            catalogEventService.handleProductCreatedEvent(event);

            verify(inventoryItemRepository, never()).save(any(InventoryItem.class));

            ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
            verify(processedEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventId()).isEqualTo(eventId);
        }
    }

    @Nested
    @DisplayName("handleProductRetiredEvent")
    class HandleProductRetiredEventTests {

        @Test
        @DisplayName("should mark inventory item as inactive (productActive=false) and record processed event when product is retired")
        void shouldMarkInventoryItemAsInactiveWhenProductRetired() {
            UUID eventId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            ProductRetiredEvent event = new ProductRetiredEvent(
                    eventId,
                    productId,
                    "SKU-100",
                    OffsetDateTime.now(),
                    1
            );

            InventoryItem existingItem = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .productId(productId)
                    .availableQuantity(25)
                    .reservedQuantity(3)
                    .productActive(true)
                    .build();

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(inventoryItemRepository.findByProductId(productId)).thenReturn(Optional.of(existingItem));

            catalogEventService.handleProductRetiredEvent(event);

            assertThat(existingItem.isProductActive()).isFalse();

            ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
            verify(processedEventRepository).save(eventCaptor.capture());

            ProcessedEvent savedEvent = eventCaptor.getValue();
            assertThat(savedEvent.getEventId()).isEqualTo(eventId);
            assertThat(savedEvent.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("should skip processing when ProductRetired event has already been processed (idempotency)")
        void shouldSkipWhenProductRetiredEventAlreadyProcessed() {
            UUID eventId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            ProductRetiredEvent event = new ProductRetiredEvent(
                    eventId,
                    productId,
                    "SKU-100",
                    OffsetDateTime.now(),
                    1
            );

            when(processedEventRepository.existsById(eventId)).thenReturn(true);

            catalogEventService.handleProductRetiredEvent(event);

            verify(processedEventRepository).existsById(eventId);
            verifyNoInteractions(inventoryItemRepository);
            verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        }

        @Test
        @DisplayName("should record processed event gracefully when retired product is not found in inventory")
        void shouldRecordProcessedEventWhenRetiredProductNotFound() {
            UUID eventId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            ProductRetiredEvent event = new ProductRetiredEvent(
                    eventId,
                    productId,
                    "SKU-UNKNOWN",
                    OffsetDateTime.now(),
                    1
            );

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(inventoryItemRepository.findByProductId(productId)).thenReturn(Optional.empty());

            catalogEventService.handleProductRetiredEvent(event);

            ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
            verify(processedEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventId()).isEqualTo(eventId);
        }
    }
}
