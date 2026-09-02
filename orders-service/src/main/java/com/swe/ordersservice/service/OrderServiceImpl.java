package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderItem;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.event.OrderCreatedEvent;
import com.swe.ordersservice.event.OrderCreatedItem;
import com.swe.ordersservice.exception.OrderNotFoundException;
import com.swe.ordersservice.outbox.OutboxEvent;
import com.swe.ordersservice.outbox.OutboxEventFactory;
import com.swe.ordersservice.outbox.OutboxEventRepository;
import com.swe.ordersservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        // 1. Create the Order
        Order order = Order.builder()
                .customerId(request.customerId())
                .status(OrderStatus.PENDING)
                .build();

        // 2. Create OrderItems and associate them with the Order
        for (OrderItemRequest itemRequest : request.items()) {

            OrderItem orderItem = OrderItem.builder()
                    .productId(itemRequest.productId())
                    .quantity(itemRequest.quantity())
                    .build();

            order.addItem(orderItem);
        }

        // 3. Persist the Order and all its items
        Order savedOrder = orderRepository.save(order);

        // 4. Create an OrderCreatedEvent and save it to the outbox
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                savedOrder.getId(),
                savedOrder.getCustomerId(),
                savedOrder.getItems().stream()
                        .map(item -> new OrderCreatedItem(
                                item.getProductId(),
                                item.getQuantity()
                        ))
                        .toList(),
                Instant.now(),
                1
        );

        // 5. Serialize the event and save it to the outbox
        OutboxEvent outboxEvent = outboxEventFactory.create(event);

        outboxEventRepository.save(outboxEvent);

        // 6. Return the API response
        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getStatus()
        );
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
}
