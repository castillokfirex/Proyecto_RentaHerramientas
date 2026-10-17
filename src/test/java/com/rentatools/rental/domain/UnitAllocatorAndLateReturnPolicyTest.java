package com.rentatools.rental.domain;

import com.rentatools.rental.domain.exception.InsufficientAvailabilityException;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.domain.model.reservation.UnitCommitment;
import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.rental.domain.service.LateReturnPolicy;
import com.rentatools.rental.domain.service.UnitAllocator;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Ids;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitAllocatorAndLateReturnPolicyTest {

    private final UnitAllocator unitAllocator = new UnitAllocator();
    private final LateReturnPolicy lateReturnPolicy = new LateReturnPolicy();

    private static final UUID TOOL_ID = Ids.newId();
    private static final UnitId UNIT_1 = UnitId.of(Ids.newId());
    private static final UnitId UNIT_2 = UnitId.of(Ids.newId());
    private static final UnitId UNIT_3 = UnitId.of(Ids.newId());

    @Test
    void shouldFilterOutUnitsOccupiedByOverlappingDatesAndAllocateFirstAvailable() {
        DateRange requested = DateRange.of(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 25));

        // UNIT_1 tiene reserva CONFIRMED solapada (18 al 21) -> ocupada
        UnitCommitment overlappingConfirmed = UnitCommitment.forReservation(
                UNIT_1,
                ReservationStatus.CONFIRMED,
                DateRange.of(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 21))
        );
        // UNIT_2 tiene reserva CANCELLED en las mismas fechas -> NO bloquea
        UnitCommitment cancelledSameDates = UnitCommitment.forReservation(
                UNIT_2,
                ReservationStatus.CANCELLED,
                requested
        );
        // UNIT_3 tiene reserva CONFIRMED sin solapamiento (26 al 28) -> NO bloquea
        UnitCommitment nonOverlapping = UnitCommitment.forReservation(
                UNIT_3,
                ReservationStatus.CONFIRMED,
                DateRange.of(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28))
        );

        List<UnitId> allocated = unitAllocator.allocate(
                TOOL_ID,
                List.of(UNIT_1, UNIT_2, UNIT_3),
                List.of(overlappingConfirmed, cancelledSameDates, nonOverlapping),
                requested,
                2
        );

        assertThat(allocated).containsExactly(UNIT_2, UNIT_3);
    }

    @Test
    void shouldFilterOutUnitsWithLateReturnEvenWhenDatesDoNotOverlap() {
        DateRange requested = DateRange.of(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));

        // UNIT_1 tenía reserva del 10 al 15 de octubre, pero sigue ACTIVE sin inspección RETURN (devolución demorada)
        UnitCommitment lateActiveUnit = UnitCommitment.activeUnreturned(
                UNIT_1,
                DateRange.of(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 15))
        );

        List<UnitId> allocated = unitAllocator.allocate(
                TOOL_ID,
                List.of(UNIT_1, UNIT_2),
                List.of(lateActiveUnit),
                requested,
                1
        );

        assertThat(allocated).containsExactly(UNIT_2);
    }

    @Test
    void shouldThrowInsufficientAvailabilityExceptionWhenNotEnoughUnitsAvailable() {
        DateRange requested = DateRange.of(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));

        UnitCommitment c1 = UnitCommitment.forReservation(UNIT_1, ReservationStatus.PENDING, requested);
        UnitCommitment c2 = UnitCommitment.activeUnreturned(
                UNIT_2,
                DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5))
        );

        assertThatThrownBy(() -> unitAllocator.allocate(
                TOOL_ID,
                List.of(UNIT_1, UNIT_2, UNIT_3),
                List.of(c1, c2),
                requested,
                2
        ))
                .isInstanceOf(InsufficientAvailabilityException.class)
                .satisfies(ex -> {
                    InsufficientAvailabilityException iae = (InsufficientAvailabilityException) ex;
                    assertThat(iae.code()).isEqualTo("INSUFFICIENT_AVAILABILITY");
                    assertThat(iae.toolId()).isEqualTo(TOOL_ID);
                    assertThat(iae.requested()).isEqualTo(2);
                    assertThat(iae.available()).isEqualTo(1);
                });
    }

    @Test
    void shouldCalculateLateDaysCorrectly() {
        LocalDate endDate = LocalDate.of(2026, 10, 20);

        // Devuelta antes de tiempo -> 0 días
        assertThat(lateReturnPolicy.calculateLateDays(endDate, LocalDate.of(2026, 10, 19))).isZero();
        // Devuelta el mismo día de fin -> 0 días
        assertThat(lateReturnPolicy.calculateLateDays(endDate, LocalDate.of(2026, 10, 20))).isZero();
        // Devuelta 3 días después -> 3 días de mora
        assertThat(lateReturnPolicy.calculateLateDays(endDate, LocalDate.of(2026, 10, 23))).isEqualTo(3);

        // Con Instant y ZoneId (America/Lima UTC-5)
        ZoneId lima = ZoneId.of("America/Lima");
        Instant returnInstant = Instant.parse("2026-10-22T15:30:00Z"); // 2026-10-22 10:30 en Lima
        assertThat(lateReturnPolicy.calculateLateDays(endDate, returnInstant, lima)).isEqualTo(2);
    }
}
