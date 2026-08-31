package com.swe.ordersservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity @Table(name = "order_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_order_product",
                columnNames = {"order_id", "product_id"}))
@AllArgsConstructor @NoArgsConstructor
@Getter @Setter @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "order")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
