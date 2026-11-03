package com.swe.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Money {

    private static final int SCALE = 2;

    @Column(name = "amount", nullable = false, precision = 19, scale = SCALE)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    public static Money of(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }

        String normalizedCurrency = currency.trim().toUpperCase();

        try {
            Currency.getInstance(normalizedCurrency);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid ISO 4217 currency code: " + normalizedCurrency, exception);
        }

        BigDecimal normalizedAmount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);

        return new Money(normalizedAmount, normalizedCurrency);
    }
}
