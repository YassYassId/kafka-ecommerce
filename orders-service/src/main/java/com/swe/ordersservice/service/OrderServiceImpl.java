package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderItem;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.entity.ProcessedEvent;
import com.swe.ordersservice.event.InventoryRejectedEvent;
import com.swe.ordersservice.event.InventoryReservedEvent;
import com.swe.ordersservice.event.OrderCreatedEvent;
import com.swe.ordersservice.event.OrderCreatedItem;
import com.swe.ordersservice.exception.InvalidIdempotencyKeyException;
import com.swe.ordersservice.exception.OrderNotFoundException;
import com.swe.ordersservice.metrics.AfterCommitExecutor;
import com.swe.ordersservice.metrics.OrderMetrics;
import com.swe.ordersservice.outbox.OutboxEvent;
import com.swe.ordersservice.outbox.OutboxEventFactory;
import com.swe.ordersservice.outbox.OutboxEventRepository;
import com.swe.ordersservice.repository.OrderRepository;
import com.swe.ordersservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;

    private final OrderMetrics orderMetrics;
    private final AfterCommitExecutor afterCommitExecutor;
    private final OrderCreationService orderCreationService;

    @Override
    public OrderResponse createOrder(String idempotencyKey, OrderRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidIdempotencyKeyException();
        }

        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent()) {
            log.info("Idempotent order request: returning existing order");
            return toResponse(existingOrder.get());
        }

        try {
            return orderCreationService.create(idempotencyKey, request);

        } catch (DataIntegrityViolationException ex) {

            Optional<Order> concurrentOrder = orderRepository.findByIdempotencyKey(idempotencyKey);

            if (concurrentOrder.isPresent()) {
                log.info("Concurrent idempotent request: returning existing order");
                return toResponse(concurrentOrder.get());
            }

            // Not an idempotency-key collision.
            // Preserve the original database error.
            throw ex;
        }
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return new OrderResponse(
                order.getId(),
                order.getStatus()
        );
    }

    @Override
    @Transactional
    public void confirmOrder(InventoryReservedEvent event) {
        boolean transitioned = updateOrderStatus(event.eventId(), event.orderId(), OrderStatus.CONFIRMED);

        if (!transitioned) {
            return;
        }

        afterCommitExecutor.execute(orderMetrics::orderConfirmed);

        log.info("Order confirmed");
    }

    @Override
    @Transactional
    public void cancelOrder(InventoryRejectedEvent event) {
        boolean transitioned = updateOrderStatus(event.eventId(), event.orderId(), OrderStatus.CANCELLED);

        if (!transitioned) {
            return;
        }

        afterCommitExecutor.execute(orderMetrics::orderCancelled);

        log.info("Order cancelled");
    }

    private boolean updateOrderStatus(UUID eventId, UUID orderId, OrderStatus targetStatus) {
        // Check if the event has already been processed (idempotency)
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Skipping already processed order lifecycle event");
            return false;
        }

        // Fetch the order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Check if the order is in the expected status
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Order " + order.getId()
                    + " cannot transition from " + order.getStatus() + " to " + targetStatus);
        }

        // Update the order status
        order.setStatus(targetStatus);

        // Save the order
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .processedAt(OffsetDateTime.now())
                .build());

        return true;
    }
}
