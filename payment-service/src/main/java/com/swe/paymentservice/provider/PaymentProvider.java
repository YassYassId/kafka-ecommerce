package com.swe.paymentservice.provider;

public interface PaymentProvider {
    PaymentProviderResult process(PaymentProviderRequest request);
}
