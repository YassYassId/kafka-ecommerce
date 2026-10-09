package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.outbox.OutboxEvent;
import com.swe.ordersservice.outbox.OutboxEventRepository;
import com.swe.ordersservice.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderCreationConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.swe.ordersservice.repository.OrderItemsRepository orderItemsRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("should safely handle concurrent order creation with the same idempotency key without duplicate orders or errors")
    void shouldHandleConcurrentOrderCreationsWithSameIdempotencyKeySafely() throws Exception {
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfThreads);

        String idempotencyKey = "concurrency-same-key-" + UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID product1Id = UUID.randomUUID();
        UUID product2Id = UUID.randomUUID();

        OrderRequest request = new OrderRequest(
                customerId,
                List.of(
                        new OrderItemRequest(product1Id, 2),
                        new OrderItemRequest(product2Id, 1)
                )
        );

        List<Future<OrderResponse>> futures = new ArrayList<>();
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    // Wait for all threads to be ready to maximize race condition probability
                    startGate.await();
                    return orderService.createOrder(idempotencyKey, request);
                } catch (Throwable t) {
                    exceptions.add(t);
                    throw t;
                } finally {
                    endGate.countDown();
                }
            }));
        }

        // Release all threads simultaneously
        startGate.countDown();
        boolean completedInTime = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completedInTime).isTrue();
        assertThat(exceptions).isEmpty();

        List<OrderResponse> responses = new ArrayList<>();
        for (Future<OrderResponse> future : futures) {
            responses.add(future.get());
        }

        // 1. Verify all threads received a valid response with PENDING status
        assertThat(responses).hasSize(numberOfThreads);
        assertThat(responses).allMatch(resp -> resp.status() == OrderStatus.PENDING);

        // 2. Verify all threads received the EXACT SAME orderId
        Set<UUID> distinctOrderIds = responses.stream()
                .map(OrderResponse::orderId)
                .collect(Collectors.toSet());
        assertThat(distinctOrderIds).hasSize(1);

        UUID agreedOrderId = distinctOrderIds.iterator().next();
        assertThat(agreedOrderId).isNotNull();

        // 3. Verify exactly 1 order was persisted in database
        var persistedOrderOpt = orderRepository.findByIdempotencyKey(idempotencyKey);
        assertThat(persistedOrderOpt).isPresent();
        Order persistedOrder = persistedOrderOpt.get();
        assertThat(persistedOrder.getId()).isEqualTo(agreedOrderId);
        assertThat(persistedOrder.getCustomerId()).isEqualTo(customerId);

        var persistedItems = orderItemsRepository.findAll().stream()
                .filter(item -> agreedOrderId.equals(item.getOrder().getId()))
                .toList();
        assertThat(persistedItems).hasSize(2);

        // 4. Verify exactly 1 OutboxEvent exists for this order
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll().stream()
                .filter(event -> agreedOrderId.equals(event.getAggregateId()))
                .toList();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.getFirst().getEventType()).isEqualTo("OrderCreated");
    }

    @Test
    @DisplayName("should safely handle concurrent order creations with distinct idempotency keys")
    void shouldHandleConcurrentOrderCreationsWithDistinctIdempotencyKeysSafely() throws Exception {
        int numberOfThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfThreads);

        List<String> idempotencyKeys = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            idempotencyKeys.add("concurrency-distinct-" + i + "-" + UUID.randomUUID());
        }

        List<Future<OrderResponse>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            final String key = idempotencyKeys.get(i);
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            futures.add(executor.submit(() -> {
                startGate.await();
                try {
                    return orderService.createOrder(key, request);
                } finally {
                    endGate.countDown();
                }
            }));
        }

        startGate.countDown();
        boolean completedInTime = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completedInTime).isTrue();

        List<OrderResponse> responses = new ArrayList<>();
        for (Future<OrderResponse> future : futures) {
            responses.add(future.get());
        }

        assertThat(responses).hasSize(numberOfThreads);

        Set<UUID> distinctOrderIds = responses.stream()
                .map(OrderResponse::orderId)
                .collect(Collectors.toSet());
        assertThat(distinctOrderIds).hasSize(numberOfThreads);

        for (String key : idempotencyKeys) {
            assertThat(orderRepository.findByIdempotencyKey(key)).isPresent();
        }
    }
}
