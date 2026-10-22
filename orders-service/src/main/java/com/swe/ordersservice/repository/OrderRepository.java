package com.swe.ordersservice.repository;

import com.swe.ordersservice.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByIdempotencyKeyAndCustomerId(String idempotencyKey, UUID customerId);
}
