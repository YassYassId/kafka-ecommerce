package com.swe.ordersservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity @Table(name = "order_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_order_product",
                columnNames = {"order_id", "product_id"}))
@AllArgsConstructor @NoArgsConstructor
@Data @Builder
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
