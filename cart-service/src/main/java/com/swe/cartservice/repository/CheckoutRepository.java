package com.swe.cartservice.repository;

import com.swe.cartservice.model.CheckoutClaim;
import com.swe.cartservice.model.CheckoutClaimResult;
import com.swe.cartservice.model.CheckoutRecord;

import java.util.Optional;
import java.util.UUID;

public interface CheckoutRepository {

    Optional<CheckoutRecord> findByCustomerIdAndKey(UUID customerId, String idempotencyKey);

    CheckoutClaimResult acquireCheckout(UUID customerId, String idempotencyKey, String expectedCartJson, String leaseToken);

    Optional<CheckoutClaim> findClaim(UUID customerId);

    boolean releaseClaim(UUID customerId, String idempotencyKey, String leaseToken);

    boolean completeCheckout(CheckoutRecord record, String expectedCartJson, String leaseToken);
}
