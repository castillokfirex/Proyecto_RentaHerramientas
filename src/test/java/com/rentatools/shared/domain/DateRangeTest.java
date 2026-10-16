package com.rentatools.shared.domain;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @Test
    void rejectsEndBeforeStart() {
        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate end = LocalDate.of(2026, 10, 19);

        assertThatThrownBy(() -> new DateRange(start, end))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("end < start")
                .extracting("code")
                .isEqualTo("DATE_RANGE_INVALID");
    }

    @Test
    void calculatesInclusiveDaysCorrectly() {
        LocalDate day1 = LocalDate.of(2026, 10, 16);
        LocalDate day3 = LocalDate.of(2026, 10, 18);

        DateRange singleDay = new DateRange(day1, day1);
        DateRange threeDays = new DateRange(day1, day3);

        assertThat(singleDay.days()).isEqualTo(1L);
        assertThat(threeDays.days()).isEqualTo(3L);
    }

    @Test
    void detectsOverlappingAndNonOverlappingRangesCorrectly() {
        DateRange base = new DateRange(
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 15)
        );

        DateRange partialOverlapRight = new DateRange(
                LocalDate.of(2026, 10, 14),
                LocalDate.of(2026, 10, 20)
        );
        DateRange partialOverlapLeft = new DateRange(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 11)
        );
        DateRange touchingEndBoundary = new DateRange(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 18)
        );
        DateRange touchingStartBoundary = new DateRange(
                LocalDate.of(2026, 10, 8),
                LocalDate.of(2026, 10, 10)
        );
        DateRange containedInside = new DateRange(
                LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 13)
        );
        DateRange disjointBefore = new DateRange(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 9)
        );
        DateRange disjointAfter = new DateRange(
                LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 20)
        );

        assertThat(base.overlaps(partialOverlapRight)).isTrue();
        assertThat(base.overlaps(partialOverlapLeft)).isTrue();
        assertThat(base.overlaps(touchingEndBoundary)).isTrue();
        assertThat(base.overlaps(touchingStartBoundary)).isTrue();
        assertThat(base.overlaps(containedInside)).isTrue();
        assertThat(containedInside.overlaps(base)).isTrue();

        assertThat(base.overlaps(disjointBefore)).isFalse();
        assertThat(base.overlaps(disjointAfter)).isFalse();
    }
}
