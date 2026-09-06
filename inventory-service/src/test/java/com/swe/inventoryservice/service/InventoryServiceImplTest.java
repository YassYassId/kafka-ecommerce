package com.swe.inventoryservice.service;

import com.swe.inventoryservice.event.OrderCreatedEvent;
import com.swe.inventoryservice.event.OrderCreatedItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryTransactionService inventoryTransactionService;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @Test
    @DisplayName("should delegate order processing to InventoryTransactionService")
    void shouldDelegateProcessingToTransactionService() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new OrderCreatedItem(UUID.randomUUID(), 2)),
                Instant.now(),
                1
        );

        inventoryService.processOrder(event);

        verify(inventoryTransactionService).process(event);
    }
}
