package com.swe.ordersservice.repository;

import com.swe.ordersservice.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderItemsRepository extends JpaRepository<OrderItem, UUID> {
}
