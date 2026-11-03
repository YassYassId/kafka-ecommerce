package com.swe.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "business_key", nullable = false, unique = true, length = 255)
    private String businessKey;

    @Embedded
    private Money money;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Payment(UUID orderId, String businessKey, Money money) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }

        if (businessKey == null || businessKey.isBlank()) {
            throw new IllegalArgumentException("Business key must not be blank");
        }

        if (money == null) {
            throw new IllegalArgumentException("Money must not be null");
        }

        Instant now = Instant.now();

        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.businessKey = businessKey.trim();
        this.money = money;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSucceeded(String providerReference) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a pending payment can succeed"
            );
        }

        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Provider reference must not be blank"
            );
        }

        this.status = PaymentStatus.SUCCEEDED;
        this.providerReference = providerReference.trim();
        this.failureCode = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String failureCode) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a pending payment can fail"
            );
        }

        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Failure code must not be blank"
            );
        }

        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode.trim();
        this.updatedAt = Instant.now();
    }
}