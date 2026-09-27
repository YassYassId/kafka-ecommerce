package com.swe.inventoryservice.repository;

import com.swe.inventoryservice.entity.InventoryItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryItemRepositoryTest {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("should save and retrieve inventory item by product ID")
    void shouldSaveAndRetrieveInventoryItem() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder()
                .productId(productId)
                .availableQuantity(50)
                .reservedQuantity(5)
                .build();

        InventoryItem savedItem = inventoryItemRepository.saveAndFlush(item);

        assertThat(savedItem.getId()).isNotNull();
        assertThat(savedItem.getProductId()).isEqualTo(productId);
        assertThat(savedItem.getAvailableQuantity()).isEqualTo(50);
        assertThat(savedItem.getReservedQuantity()).isEqualTo(5);

        entityManager.clear();

        Optional<InventoryItem> retrieved = inventoryItemRepository.findByProductId(productId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(savedItem.getId());
        assertThat(retrieved.get().getAvailableQuantity()).isEqualTo(50);
        assertThat(retrieved.get().getReservedQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("should fail when duplicate product ID is inserted due to unique constraint")
    void shouldFailWhenDuplicateProductIdInserted() {
        UUID productId = UUID.randomUUID();

        InventoryItem item1 = InventoryItem.builder()
                .productId(productId)
                .availableQuantity(10)
                .reservedQuantity(0)
                .build();
        inventoryItemRepository.saveAndFlush(item1);

        InventoryItem item2 = InventoryItem.builder()
                .productId(productId)
                .availableQuantity(20)
                .reservedQuantity(0)
                .build();

        assertThatThrownBy(() -> inventoryItemRepository.saveAndFlush(item2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should fail when available quantity is negative due to check constraint")
    void shouldFailWhenAvailableQuantityIsNegative() {
        InventoryItem item = InventoryItem.builder()
                .productId(UUID.randomUUID())
                .availableQuantity(-5)
                .reservedQuantity(0)
                .build();

        assertThatThrownBy(() -> inventoryItemRepository.saveAndFlush(item))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should fail when reserved quantity is negative due to check constraint")
    void shouldFailWhenReservedQuantityIsNegative() {
        InventoryItem item = InventoryItem.builder()
                .productId(UUID.randomUUID())
                .availableQuantity(10)
                .reservedQuantity(-1)
                .build();

        assertThatThrownBy(() -> inventoryItemRepository.saveAndFlush(item))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should return true when inventory item exists by product ID and false otherwise")
    void shouldCheckExistenceByProductId() {
        UUID existingProductId = UUID.randomUUID();
        UUID nonExistingProductId = UUID.randomUUID();

        InventoryItem item = InventoryItem.builder()
                .productId(existingProductId)
                .availableQuantity(20)
                .reservedQuantity(0)
                .productActive(true)
                .build();

        inventoryItemRepository.saveAndFlush(item);
        entityManager.clear();

        assertThat(inventoryItemRepository.existsByProductId(existingProductId)).isTrue();
        assertThat(inventoryItemRepository.existsByProductId(nonExistingProductId)).isFalse();
    }

    @Test
    @DisplayName("should persist and retrieve productActive status correctly")
    void shouldPersistAndRetrieveProductActiveStatus() {
        UUID productIdActive = UUID.randomUUID();
        UUID productIdInactive = UUID.randomUUID();

        InventoryItem activeItem = InventoryItem.builder()
                .productId(productIdActive)
                .availableQuantity(15)
                .reservedQuantity(0)
                .productActive(true)
                .build();

        InventoryItem inactiveItem = InventoryItem.builder()
                .productId(productIdInactive)
                .availableQuantity(0)
                .reservedQuantity(0)
                .productActive(false)
                .build();

        inventoryItemRepository.saveAndFlush(activeItem);
        inventoryItemRepository.saveAndFlush(inactiveItem);
        entityManager.clear();

        Optional<InventoryItem> retrievedActive = inventoryItemRepository.findByProductId(productIdActive);
        Optional<InventoryItem> retrievedInactive = inventoryItemRepository.findByProductId(productIdInactive);

        assertThat(retrievedActive).isPresent();
        assertThat(retrievedActive.get().isProductActive()).isTrue();

        assertThat(retrievedInactive).isPresent();
        assertThat(retrievedInactive.get().isProductActive()).isFalse();
    }
}
