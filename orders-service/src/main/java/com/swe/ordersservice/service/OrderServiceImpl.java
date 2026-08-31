package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderItem;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

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

        // 4. Return the API response
        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getStatus()
        );
    }
}
