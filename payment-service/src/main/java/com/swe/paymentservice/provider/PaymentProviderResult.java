package com.swe.paymentservice.provider;

public record PaymentProviderResult(
        boolean successful,
        String providerReference,
        String failureCode
) {
    public static PaymentProviderResult success(String providerReference) {
        return new PaymentProviderResult(true, providerReference, null);
    }

    public static PaymentProviderResult failure(String failureCode) {
        return new PaymentProviderResult(false, null, failureCode);
    }
}
