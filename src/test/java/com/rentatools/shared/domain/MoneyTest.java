package com.rentatools.shared.domain;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void defaultsToCopAndScaleTwoWithHalfUpRounding() {
        Money roundedUp = Money.of("1234.565");
        Money roundedDown = Money.of("1234.564");
        Money exactScale = Money.of("50000");

        assertThat(roundedUp.amount()).isEqualByComparingTo("1234.57");
        assertThat(roundedUp.amount().scale()).isEqualTo(2);
        assertThat(roundedUp.currency()).isEqualTo(Currency.getInstance("COP"));

        assertThat(roundedDown.amount()).isEqualByComparingTo("1234.56");
        assertThat(exactScale.amount()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(Money.ZERO.amount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(Money.ZERO.currency()).isEqualTo(Currency.getInstance("COP"));
    }

    @Test
    void addsTwoMoneyValuesCorrectly() {
        Money a = Money.of("15000.25");
        Money b = Money.of("4999.75");

        Money sum = a.plus(b);

        assertThat(sum).isEqualTo(Money.of("20000.00"));
        assertThat(sum.currency()).isEqualTo(Money.DEFAULT_CURRENCY);
    }

    @Test
    void subtractsTwoMoneyValuesCorrectly() {
        Money a = Money.of("20000.00");
        Money b = Money.of("7500.40");

        Money difference = a.minus(b);

        assertThat(difference).isEqualTo(Money.of("12499.60"));
        assertThat(difference.isNegative()).isFalse();

        Money negativeResult = b.minus(a);
        assertThat(negativeResult).isEqualTo(Money.of("-12499.60"));
        assertThat(negativeResult.isNegative()).isTrue();
    }

    @Test
    void multipliesByLongAndBigDecimalWithHalfUpRounding() {
        Money base = Money.of("33333.33");

        Money byLong = base.times(3L);
        Money byDecimal = Money.of("100.00").times(new BigDecimal("0.185"));

        assertThat(byLong).isEqualTo(Money.of("99999.99"));
        assertThat(byDecimal).isEqualTo(Money.of("18.50"));

        Money halfUpOnMultiply = Money.of("10.01").times(new BigDecimal("1.005"));
        // 10.01 * 1.005 = 10.06005 -> 10.06
        assertThat(halfUpOnMultiply).isEqualTo(Money.of("10.06"));

        Money halfUpTrigger = Money.of("1.00").times(new BigDecimal("2.345"));
        // 1.00 * 2.345 = 2.345 -> 2.35 (HALF_UP)
        assertThat(halfUpTrigger).isEqualTo(Money.of("2.35"));
    }

    @Test
    void comparesAndSelectsMinAndMax() {
        Money smaller = Money.of("10000.00");
        Money larger = Money.of("25000.00");

        assertThat(smaller.compareTo(larger)).isLessThan(0);
        assertThat(larger.compareTo(smaller)).isGreaterThan(0);
        assertThat(smaller.min(larger)).isEqualTo(smaller);
        assertThat(smaller.max(larger)).isEqualTo(larger);
    }

    @Test
    void rejectsOperationsBetweenDifferentCurrencies() {
        Money cop = Money.of("10000.00", "COP");
        Money usd = Money.of("10.00", "USD");

        assertThatThrownBy(() -> cop.plus(usd))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("CURRENCY_MISMATCH");
    }
}
