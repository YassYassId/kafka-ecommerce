package com.swe.ordersservice.repository;

import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderItem;
import com.swe.ordersservice.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("should save and retrieve order with cascaded items and auto-generated UUID/timestamps")
    void shouldSaveAndRetrieveOrderWithCascadedItems() {
        // 1. Create Order
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Order order = Order.builder()
                .customerId(customerId)
                .status(OrderStatus.PENDING)
                .build();

        // 2. Add item using domain helper method
        order.addItem(OrderItem.builder()
                .productId(productId)
                .quantity(2)
                .build());

        // 3. Save and flush (cascades to items)
        Order savedOrder = orderRepository.saveAndFlush(order);

        assertThat(savedOrder.getId()).isNotNull();
        assertThat(savedOrder.getCreatedAt()).isNotNull();
        assertThat(savedOrder.getUpdatedAt()).isNotNull();
        assertThat(savedOrder.getItems()).hasSize(1);
        assertThat(savedOrder.getItems().getFirst().getId()).isNotNull();

        UUID orderId = savedOrder.getId();
        UUID itemId = savedOrder.getItems().getFirst().getId();

        // 4. Clear persistence context to force fresh SELECT from DB
        entityManager.clear();

        // 5. Retrieve the Order from database
        Order retrievedOrder = orderRepository.findById(orderId).orElseThrow();

        assertThat(retrievedOrder.getId()).isEqualTo(orderId);
        assertThat(retrievedOrder.getCustomerId()).isEqualTo(customerId);
        assertThat(retrievedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(retrievedOrder.getItems()).hasSize(1);

        OrderItem retrievedItem = retrievedOrder.getItems().getFirst();
        assertThat(retrievedItem.getId()).isEqualTo(itemId);
        assertThat(retrievedItem.getProductId()).isEqualTo(productId);
        assertThat(retrievedItem.getQuantity()).isEqualTo(2);
        assertThat(retrievedItem.getOrder().getId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("should fail when quantity is zero or negative due to database check constraint")
    void shouldFailWhenQuantityIsZeroOrNegative() {
        Order savedOrder = orderRepository.saveAndFlush(Order.builder()
                .customerId(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .build());

        OrderItem invalidItem = OrderItem.builder()
                .order(savedOrder)
                .productId(UUID.randomUUID())
                .quantity(-1) // Violates DB CHECK (quantity > 0)
                .build();

        assertThatThrownBy(() -> orderItemsRepository.saveAndFlush(invalidItem))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should cascade delete order items when order is deleted")
    void shouldCascadeDeleteOrderItemsWhenOrderIsDeleted() {
        // 1. Create order with item
        Order order = Order.builder()
                .customerId(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .build();

        order.addItem(OrderItem.builder()
                .productId(UUID.randomUUID())
                .quantity(1)
                .build());

        Order savedOrder = orderRepository.saveAndFlush(order);
        UUID orderId = savedOrder.getId();
        UUID itemId = savedOrder.getItems().getFirst().getId();

        // Verify entities are present in DB
        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(orderItemsRepository.findById(itemId)).isPresent();

        // 2. Clear persistence context before delete
        entityManager.clear();

        // 3. Delete order
        orderRepository.deleteById(orderId);
        orderRepository.flush();

        // 4. Verify cascade deletion removed both order and child item
        assertThat(orderRepository.findById(orderId)).isEmpty();
        assertThat(orderItemsRepository.findById(itemId)).isEmpty();
    }

    @Test
    @DisplayName("should fail when same product is added twice to the same order due to unique constraint")
    void shouldFailWhenSameProductIsAddedTwiceToSameOrder() {
        Order order = Order.builder()
                .customerId(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .build();

        UUID productId = UUID.randomUUID();

        // Add first item
        order.addItem(OrderItem.builder()
                .productId(productId)
                .quantity(2)
                .build());

        // Add second item with the same product ID to same order
        order.addItem(OrderItem.builder()
                .productId(productId)
                .quantity(3)
                .build());

        // Flushed insert should violate uq_order_product
        assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}