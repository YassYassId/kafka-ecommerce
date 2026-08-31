package com.swe.ordersservice.repository;

import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderItem;
import com.swe.ordersservice.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
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
    void shouldSaveAndRetrieveOrderWithItems() {
        // 1. Create and save an Order
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .customerId(customerId)
                .status(OrderStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        Order savedOrder = orderRepository.saveAndFlush(order);

        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getId()).isEqualTo(orderId);

        // 2. Create and save an OrderItem associated with the Order
        UUID productId = UUID.randomUUID();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(savedOrder)
                .productId(productId)
                .quantity(2)
                .build();

        OrderItem savedItem = orderItemsRepository.saveAndFlush(item);

        assertThat(savedItem).isNotNull();
        assertThat(savedItem.getOrder().getId()).isEqualTo(orderId);

        // 3. Retrieve the Order from the database
        entityManager.clear();

        Order retrievedOrder = orderRepository.findById(orderId)
                .orElseThrow();

        // 4. Verify the retrieved Order
        assertThat(retrievedOrder.getId())
                .isEqualTo(orderId);

        assertThat(retrievedOrder.getCustomerId())
                .isEqualTo(customerId);

        assertThat(retrievedOrder.getStatus())
                .isEqualTo(OrderStatus.PENDING);

        // 5. Retrieve the OrderItem from the database
        OrderItem retrievedItem = orderItemsRepository
                .findById(savedItem.getId())
                .orElseThrow();

        assertThat(retrievedItem.getProductId())
                .isEqualTo(productId);

        assertThat(retrievedItem.getQuantity())
                .isEqualTo(2);

        assertThat(retrievedItem.getOrder().getId())
                .isEqualTo(orderId);
    }
    @Test
    void shouldFailWhenQuantityIsZeroOrNegative() {
        // Create an Order
        Order savedOrder = orderRepository.save(Order.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());
        // Attempt to save an item with a negative quantity
        OrderItem invalidItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(savedOrder)
                .productId(UUID.randomUUID())
                .quantity(-1) // Triggers DB CHECK (quantity > 0)
                .build();
        assertThatThrownBy(() -> {
            orderItemsRepository.saveAndFlush(invalidItem);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    void shouldCascadeDeleteOrderItemsWhenOrderIsDeleted() {
        // Create Order and Item
        Order savedOrder = orderRepository.saveAndFlush(Order.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());
        OrderItem item = orderItemsRepository.saveAndFlush(OrderItem.builder()
                .id(UUID.randomUUID())
                .order(savedOrder)
                .productId(UUID.randomUUID())
                .quantity(1)
                .build());
        // Verify they exist in DB
        assertThat(orderRepository.findById(savedOrder.getId())).isPresent();
        assertThat(orderItemsRepository.findById(item.getId())).isPresent();
        // Clear the session so Hibernate forgets about the cached entities and doesn't complain about the referenced child
        entityManager.clear();
        // Delete the order (DB FK is set ON DELETE CASCADE)
        orderRepository.deleteById(savedOrder.getId());
        orderRepository.flush();
        // Verify that the order items were deleted automatically from the database
        assertThat(orderRepository.findById(savedOrder.getId())).isEmpty();
        assertThat(orderItemsRepository.findById(item.getId())).isEmpty();
    }

    @Test
    void shouldFailWhenSameProductIsAddedTwiceToSameOrder() {
        // 1. Create and save an Order
        Order savedOrder = orderRepository.saveAndFlush(
                Order.builder()
                        .id(UUID.randomUUID())
                        .customerId(UUID.randomUUID())
                        .status(OrderStatus.PENDING)
                        .createdAt(OffsetDateTime.now())
                        .updatedAt(OffsetDateTime.now())
                        .build()
        );

        // 2. Create the first OrderItem
        UUID productId = UUID.randomUUID();

        OrderItem firstItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(savedOrder)
                .productId(productId)
                .quantity(2)
                .build();

        orderItemsRepository.saveAndFlush(firstItem);

        // 3. Try to add the same product to the same order again
        OrderItem duplicateItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(savedOrder)
                .productId(productId)
                .quantity(3)
                .build();

        // 4. The database should reject the duplicate
        assertThatThrownBy(() ->
                orderItemsRepository.saveAndFlush(duplicateItem)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}