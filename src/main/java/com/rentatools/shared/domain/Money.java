package com.rentatools.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value Object inmutable para representar importes monetarios.
 * Usa moneda COP por defecto y escala 2 con redondeo {@link RoundingMode#HALF_UP}.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final Currency DEFAULT_CURRENCY = Currency.getInstance("COP");
    public static final int SCALE = 2;
    public static final Money ZERO = Money.of("0");

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        currency = (currency == null) ? DEFAULT_CURRENCY : currency;
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public Money(BigDecimal amount) {
        this(amount, DEFAULT_CURRENCY);
    }

    public static Money of(String value) {
        Objects.requireNonNull(value, "value must not be null");
        return new Money(new BigDecimal(value), DEFAULT_CURRENCY);
    }

    public static Money of(BigDecimal value) {
        return new Money(value, DEFAULT_CURRENCY);
    }

    public static Money of(BigDecimal value, Currency currency) {
        return new Money(value, currency);
    }

    public static Money of(String value, String currencyCode) {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        return new Money(new BigDecimal(value), Currency.getInstance(currencyCode));
    }

    public static Money of(long value) {
        return new Money(BigDecimal.valueOf(value), DEFAULT_CURRENCY);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money times(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor must not be null");
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public Money times(long factor) {
        return times(BigDecimal.valueOf(factor));
    }

    public Money times(int factor) {
        return times(BigDecimal.valueOf(factor));
    }

    public boolean isNegative() {
        return this.amount.signum() < 0;
    }

    public boolean isZero() {
        return this.amount.signum() == 0;
    }

    public boolean isPositive() {
        return this.amount.signum() > 0;
    }

    public Money min(Money other) {
        requireSameCurrency(other);
        return compareTo(other) <= 0 ? this : other;
    }

    public Money max(Money other) {
        requireSameCurrency(other);
        return compareTo(other) >= 0 ? this : other;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!this.currency.equals(other.currency)) {
            throw new BusinessRuleViolation(
                    "CURRENCY_MISMATCH",
                    "Cannot operate on different currencies: " + this.currency + " and " + other.currency
            );
        }
    }
}
