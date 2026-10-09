package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderItem;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.event.OrderCreatedEvent;
import com.swe.ordersservice.event.OrderCreatedItem;
import com.swe.ordersservice.metrics.AfterCommitExecutor;
import com.swe.ordersservice.metrics.OrderMetrics;
import com.swe.ordersservice.outbox.OutboxEvent;
import com.swe.ordersservice.outbox.OutboxEventFactory;
import com.swe.ordersservice.outbox.OutboxEventRepository;
import com.swe.ordersservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCreationService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final OrderMetrics orderMetrics;
    private final AfterCommitExecutor afterCommitExecutor;

    @Transactional
    public OrderResponse create(String idempotencyKey, OrderRequest request) {

        Order order = Order.builder()
                .customerId(request.customerId())
                .status(OrderStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        for (OrderItemRequest itemRequest : request.items()) {

            OrderItem orderItem = OrderItem.builder()
                    .productId(itemRequest.productId())
                    .quantity(itemRequest.quantity())
                    .build();

            order.addItem(orderItem);
        }

        // Force the unique constraint check before outbox creation.
        Order savedOrder = orderRepository.saveAndFlush(order);

        OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), savedOrder.getId(), savedOrder.getCustomerId(),
                savedOrder.getItems().stream()
                        .map(item -> new OrderCreatedItem(item.getProductId(), item.getQuantity()))
                        .toList(),
                Instant.now(),
                1
        );

        String correlationId = MDC.get("correlationId");

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.warn("Correlation ID missing while creating order; generated a new one");
        }

        OutboxEvent outboxEvent = outboxEventFactory.create(event, correlationId);

        outboxEventRepository.save(outboxEvent);

        try {
            MDC.put("orderId", savedOrder.getId().toString());
            MDC.put("eventId", event.eventId().toString());

            log.info("Order created");
        } finally {
            MDC.remove("orderId");
            MDC.remove("eventId");
        }

        afterCommitExecutor.execute(orderMetrics::orderCreated);

        return new OrderResponse(savedOrder.getId(), savedOrder.getStatus());
    }
}
