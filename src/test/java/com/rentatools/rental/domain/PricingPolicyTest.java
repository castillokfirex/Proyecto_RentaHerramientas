package com.rentatools.rental.domain;

import com.rentatools.rental.domain.model.pricing.Quote;
import com.rentatools.rental.domain.model.pricing.QuoteLine;
import com.rentatools.rental.domain.model.pricing.RateCard;
import com.rentatools.rental.domain.service.PricingPolicy;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingPolicyTest {

    private final PricingPolicy pricingPolicy = new PricingPolicy();

    // Tarifa estándar: DAY = 50, WEEK = 300, MONTH = 1000, garantía = 200
    private static final RateCard FULL_RATE_CARD = RateCard.of(
            Money.of("50.00"),
            Money.of("300.00"),
            Money.of("1000.00"),
            Money.of("200.00")
    );

    @Test
    void shouldCalculateFor3DaysUsingDailyRate() {
        // 3 días -> 3 * 50 = 150.00
        Money agreed = pricingPolicy.calculateAgreedAmount(FULL_RATE_CARD, 3L);
        assertThat(agreed).isEqualTo(Money.of("150.00"));
    }

    @Test
    void shouldCalculateFor7DaysUsingWeeklyPackage() {
        // 7 días -> 1 semana (300) vs 7 * 50 (350) -> min = 300.00
        Money agreed = pricingPolicy.calculateAgreedAmount(FULL_RATE_CARD, 7L);
        assertThat(agreed).isEqualTo(Money.of("300.00"));
    }

    @Test
    void shouldCalculateFor10DaysCombiningWeekAndRemainingDays() {
        // 10 días -> 1 semana (300) + 3 días (150) = 450.00 vs 10 * 50 (500) -> 450.00
        Money agreed = pricingPolicy.calculateAgreedAmount(FULL_RATE_CARD, 10L);
        assertThat(agreed).isEqualTo(Money.of("450.00"));
    }

    @Test
    void shouldCalculateFor30DaysUsingMonthlyPackage() {
        // 30 días -> 1 mes (1000) vs 30 * 50 (1500) -> 1000.00
        Money agreed = pricingPolicy.calculateAgreedAmount(FULL_RATE_CARD, 30L);
        assertThat(agreed).isEqualTo(Money.of("1000.00"));
    }

    @Test
    void shouldCalculateFor45DaysCombiningMonthTwoWeeksAndOneDay() {
        // 45 días -> 1 mes (1000) + 2 semanas (600) + 1 día (50) = 1650.00 vs 45 * 50 (2250) -> 1650.00
        Money agreed = pricingPolicy.calculateAgreedAmount(FULL_RATE_CARD, 45L);
        assertThat(agreed).isEqualTo(Money.of("1650.00"));
    }

    @Test
    void shouldCalculateWithoutWeeklyRateFallingBackFromMonthToDays() {
        RateCard withoutWeekly = RateCard.of(
                Money.of("50.00"),
                null,
                Money.of("1000.00"),
                Money.of("200.00")
        );

        // 10 días sin tarifa WEEK -> 10 * 50 = 500.00
        assertThat(pricingPolicy.calculateAgreedAmount(withoutWeekly, 10L))
                .isEqualTo(Money.of("500.00"));

        // 45 días sin tarifa WEEK -> 1 mes (1000) + 15 días (750) = 1750.00
        assertThat(pricingPolicy.calculateAgreedAmount(withoutWeekly, 45L))
                .isEqualTo(Money.of("1750.00"));
    }

    @Test
    void shouldCapAtDailyRateTimesDaysWhenGreedySplitIsMoreExpensive() {
        // Si la tarifa semanal (400) fuera más cara que 7 días sueltos (7 * 50 = 350),
        // min(repartoVoraz, N * DAY) elige 350.00
        RateCard expensiveWeeklyCard = RateCard.of(
                Money.of("50.00"),
                Money.of("400.00"),
                Money.of("2000.00"),
                Money.of("100.00")
        );

        assertThat(pricingPolicy.calculateAgreedAmount(expensiveWeeklyCard, 7L))
                .isEqualTo(Money.of("350.00"));
        assertThat(pricingPolicy.calculateAgreedAmount(expensiveWeeklyCard, 30L))
                .isEqualTo(Money.of("1500.00"));
    }

    @Test
    void shouldBuildQuoteWithMultipleUnitsAndTotals() {
        DateRange range = DateRange.of(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 29)); // 10 días
        QuoteLine line = pricingPolicy.quoteLine(Ids.newId(), 2, FULL_RATE_CARD, range);
        Quote quote = Quote.of(range, List.of(line));

        assertThat(quote.days()).isEqualTo(10L);
        assertThat(line.unitAgreedAmount()).isEqualTo(Money.of("450.00"));
        assertThat(line.lineAgreedAmount()).isEqualTo(Money.of("900.00"));
        assertThat(line.lineDepositAmount()).isEqualTo(Money.of("400.00"));
        assertThat(quote.totalAgreedAmount()).isEqualTo(Money.of("900.00"));
        assertThat(quote.totalDepositAmount()).isEqualTo(Money.of("400.00"));
    }
}
