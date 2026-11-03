package com.swe.paymentservice.entity;

public enum PaymentStatus {
    // Payment exists but doesn't yet have a final result.
    PENDING,
    // The provider accepted the payment.
    SUCCEEDED,
    // The provider definitively rejected/failed the payment.
    FAILED
}
