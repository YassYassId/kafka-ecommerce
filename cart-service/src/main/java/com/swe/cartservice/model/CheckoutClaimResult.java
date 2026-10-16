package com.swe.cartservice.model;

public enum CheckoutClaimResult{
    // New claim and execution lease acquired
    ACQUIRED,
    // Existing same-key claim resumed with a new lease
    RESUMED,
    // Another execution currently holds the lease
    BUSY,
    // Claim belongs to a different idempotency key
    CONFLICT,
    // Original cart no longer matches
    CART_CHANGED
}
