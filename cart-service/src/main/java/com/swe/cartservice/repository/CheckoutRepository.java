package com.swe.cartservice.repository;

import com.swe.cartservice.model.CheckoutRecord;

import java.util.Optional;
import java.util.UUID;

public interface CheckoutRepository {

    Optional<CheckoutRecord> findByCustomerIdAndKey(UUID customerId, String idempotencyKey);

    boolean completeCheckout(CheckoutRecord record, String expectedCartVersion);

    boolean claimCheckout(UUID customerId, String idempotencyKey, String expectedCartJson);

    boolean releaseClaim(UUID customerId, String idempotencyKey);
}
