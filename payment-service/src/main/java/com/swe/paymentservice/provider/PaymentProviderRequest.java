package com.swe.paymentservice.provider;

import com.swe.paymentservice.entity.Money;

import java.util.UUID;

public record PaymentProviderRequest(
        UUID paymentId,
        UUID orderId,
        Money money
) {
}
