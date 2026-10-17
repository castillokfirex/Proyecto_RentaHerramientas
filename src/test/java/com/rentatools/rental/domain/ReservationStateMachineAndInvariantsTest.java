package com.rentatools.rental.domain;

import com.rentatools.rental.domain.event.AllUnitsDelivered;
import com.rentatools.rental.domain.event.AllUnitsReturned;
import com.rentatools.rental.domain.event.ReservationCancelledEvt;
import com.rentatools.rental.domain.event.ReservationConfirmedEvt;
import com.rentatools.rental.domain.event.ReservationCreated;
import com.rentatools.rental.domain.exception.InvalidReservationTransitionException;
import com.rentatools.rental.domain.exception.MixedProvidersException;
import com.rentatools.rental.domain.exception.NotReservationParticipantException;
import com.rentatools.rental.domain.model.reservation.ClientId;
import com.rentatools.rental.domain.model.reservation.ProviderId;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationCode;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.domain.model.reservation.ReservationUnit;
import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.DomainEvent;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationStateMachineAndInvariantsTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private static final ClientId CLIENT_ID = ClientId.of(Ids.newId());
    private static final ProviderId PROVIDER_1 = ProviderId.of(Ids.newId());
    private static final ProviderId PROVIDER_2 = ProviderId.of(Ids.newId());

    @Test
    void shouldCreateReservationWithCrockfordCodeFrozenRatesAndCreatedEvent() {
        ReservationUnit unit1 = ReservationUnit.create(
                UnitId.of(Ids.newId()),
                PROVIDER_1,
                Money.of("50.00"),
                Money.of("150.00"),
                Money.of("100.00")
        );
        ReservationUnit unit2 = ReservationUnit.create(
                UnitId.of(Ids.newId()),
                PROVIDER_1,
                Money.of("80.00"),
                Money.of("240.00"),
                Money.of("120.00")
        );
        DateRange range = DateRange.of(TODAY, TODAY.plusDays(2));

        Reservation reservation = Reservation.create(CLIENT_ID, range, List.of(unit1, unit2), FIXED_CLOCK);

        assertThat(reservation.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.code().value()).hasSize(10).matches("^[0-9A-HJ-KM-NP-TV-Z]{10}$");
        assertThat(reservation.providerId()).isEqualTo(PROVIDER_1);
        assertThat(reservation.totalAgreedAmount()).isEqualTo(Money.of("390.00"));
        assertThat(reservation.totalDepositAmount()).isEqualTo(Money.of("220.00"));
        assertThat(reservation.units().get(0).dailyRate()).isEqualTo(Money.of("50.00"));
        assertThat(reservation.units().get(0).agreedAmount()).isEqualTo(Money.of("150.00"));
        assertThat(reservation.units().get(0).depositAmount()).isEqualTo(Money.of("100.00"));

        List<DomainEvent> events = reservation.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ReservationCreated.class);
    }

    @Test
    void shouldRejectInvalidCrockfordBase32Codes() {
        assertThatThrownBy(() -> ReservationCode.of("SHORT"))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INVALID_RESERVATION_CODE");

        // 'I', 'L', 'O', 'U' are forbidden in Crockford Base32
        assertThatThrownBy(() -> ReservationCode.of("ABCD12345O"))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INVALID_RESERVATION_CODE");
    }

    @Test
    void shouldRejectMixedProvidersInSameReservation() {
        ReservationUnit unitProvider1 = ReservationUnit.create(
                UnitId.of(Ids.newId()),
                PROVIDER_1,
                Money.of("50.00"),
                Money.of("100.00"),
                Money.of("50.00")
        );
        ReservationUnit unitProvider2 = ReservationUnit.create(
                UnitId.of(Ids.newId()),
                PROVIDER_2,
                Money.of("60.00"),
                Money.of("120.00"),
                Money.of("60.00")
        );
        DateRange range = DateRange.of(TODAY, TODAY.plusDays(1));

        assertThatThrownBy(() -> Reservation.create(CLIENT_ID, range, List.of(unitProvider1, unitProvider2), FIXED_CLOCK))
                .isInstanceOf(MixedProvidersException.class)
                .extracting("code")
                .isEqualTo("MIXED_PROVIDERS");

        assertThatThrownBy(() -> Reservation.create(CLIENT_ID, PROVIDER_1, range, List.of(unitProvider1, unitProvider2), FIXED_CLOCK))
                .isInstanceOf(MixedProvidersException.class);
    }

    @Test
    void shouldRejectStartDateInThePastUsingInjectedClock() {
        ReservationUnit unit = sampleUnit(PROVIDER_1);
        DateRange pastRange = DateRange.of(TODAY.minusDays(1), TODAY.plusDays(2));

        assertThatThrownBy(() -> Reservation.create(CLIENT_ID, PROVIDER_1, pastRange, List.of(unit), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("PAST_START_DATE");
    }

    @Test
    void shouldCompleteFullHappyPathLifecycleWithUnitDeliveriesAndReturns() {
        ReservationUnit unit1 = sampleUnit(PROVIDER_1);
        ReservationUnit unit2 = sampleUnit(PROVIDER_1);
        Reservation reservation = Reservation.create(
                CLIENT_ID,
                PROVIDER_1,
                DateRange.of(TODAY, TODAY.plusDays(3)),
                List.of(unit1, unit2),
                FIXED_CLOCK
        );
        reservation.pullDomainEvents();

        // 1. PENDING -> CONFIRMED
        reservation.confirm(PROVIDER_1, FIXED_CLOCK);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.pullDomainEvents())
                .singleElement()
                .isInstanceOf(ReservationConfirmedEvt.class);

        // 2. CONFIRMED -> READY_FOR_PICKUP
        reservation.markClearedForDelivery(FIXED_CLOCK);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.READY_FOR_PICKUP);

        // 3. READY_FOR_PICKUP -> ACTIVE (only when ALL units are delivered)
        reservation.registerUnitDelivery(unit1.id(), FIXED_CLOCK);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.READY_FOR_PICKUP);
        assertThat(reservation.pullDomainEvents()).isEmpty();

        reservation.registerUnitDelivery(unit2.id(), FIXED_CLOCK);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.pullDomainEvents())
                .singleElement()
                .isInstanceOf(AllUnitsDelivered.class);

        // 4. ACTIVE -> RETURNED (only when ALL units are returned)
        reservation.registerUnitReturn(unit1.id(), FIXED_CLOCK);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.pullDomainEvents()).isEmpty();

        reservation.registerUnitReturn(unit2.id(), FIXED_CLOCK);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RETURNED);
        assertThat(reservation.pullDomainEvents())
                .singleElement()
                .isInstanceOf(AllUnitsReturned.class);

        // 5. RETURNED -> CLOSED
        reservation.close(FIXED_CLOCK);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.CLOSED);
    }

    @Test
    void shouldAllowProviderToRejectPendingReservation() {
        Reservation reservation = newPendingReservation();
        reservation.reject(PROVIDER_1, "Sin disponibilidad logística", FIXED_CLOCK);

        assertThat(reservation.status()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservation.statusReason()).isEqualTo("Sin disponibilidad logística");
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"PENDING", "CONFIRMED", "READY_FOR_PICKUP"})
    void shouldAllowClientToCancelFromPendingConfirmedOrReadyForPickup(ReservationStatus initialStatus) {
        Reservation reservation = reservationInStatus(initialStatus);
        reservation.pullDomainEvents();

        reservation.cancel(CLIENT_ID, "Cambio de planes", FIXED_CLOCK);

        assertThat(reservation.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.statusReason()).isEqualTo("Cambio de planes");
        List<DomainEvent> events = reservation.pullDomainEvents();
        assertThat(events).hasSize(1);
        ReservationCancelledEvt evt = (ReservationCancelledEvt) events.get(0);
        assertThat(evt.previousStatus()).isEqualTo(initialStatus);
        assertThat(evt.reason()).isEqualTo("Cambio de planes");
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"ACTIVE", "RETURNED", "CLOSED", "REJECTED", "CANCELLED"})
    void shouldRejectCancellationFromNonCancellableStates(ReservationStatus nonCancellableStatus) {
        Reservation reservation = reservationInStatus(nonCancellableStatus);

        assertThatThrownBy(() -> reservation.cancel(CLIENT_ID, "Intento inválido", FIXED_CLOCK))
                .isInstanceOf(InvalidReservationTransitionException.class)
                .extracting("code")
                .isEqualTo("INVALID_RESERVATION_TRANSITION");
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING"})
    void shouldRejectConfirmAndRejectWhenNotPending(ReservationStatus status) {
        Reservation reservation = reservationInStatus(status);

        assertThatThrownBy(() -> reservation.confirm(FIXED_CLOCK))
                .isInstanceOf(InvalidReservationTransitionException.class);

        assertThatThrownBy(() -> reservation.reject("No", FIXED_CLOCK))
                .isInstanceOf(InvalidReservationTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"CONFIRMED"})
    void shouldRejectMarkClearedForDeliveryWhenNotConfirmed(ReservationStatus status) {
        Reservation reservation = reservationInStatus(status);

        assertThatThrownBy(() -> reservation.markClearedForDelivery(FIXED_CLOCK))
                .isInstanceOf(InvalidReservationTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"READY_FOR_PICKUP"})
    void shouldRejectDeliveryWhenNotReadyForPickup(ReservationStatus status) {
        Reservation reservation = reservationInStatus(status);
        ReservationUnit unit = reservation.units().get(0);

        assertThatThrownBy(() -> reservation.registerUnitDelivery(unit.id(), FIXED_CLOCK))
                .isInstanceOf(InvalidReservationTransitionException.class);
        assertThatThrownBy(() -> reservation.markAllUnitsDelivered(FIXED_CLOCK))
                .isInstanceOf(InvalidReservationTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"ACTIVE"})
    void shouldRejectReturnWhenNotActive(ReservationStatus status) {
        Reservation reservation = reservationInStatus(status);
        ReservationUnit unit = reservation.units().get(0);

        assertThatThrownBy(() -> reservation.registerUnitReturn(unit.id(), FIXED_CLOCK))
                .isInstanceOf(InvalidReservationTransitionException.class);
        assertThatThrownBy(() -> reservation.markAllUnitsReturned(FIXED_CLOCK))
                .isInstanceOf(InvalidReservationTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"RETURNED"})
    void shouldRejectCloseWhenNotReturned(ReservationStatus status) {
        Reservation reservation = reservationInStatus(status);

        assertThatThrownBy(() -> reservation.close(FIXED_CLOCK))
                .isInstanceOf(InvalidReservationTransitionException.class);
    }

    @Test
    void shouldRejectUnauthorizedActorOperations() {
        Reservation reservation = newPendingReservation();

        assertThatThrownBy(() -> reservation.confirm(PROVIDER_2, FIXED_CLOCK))
                .isInstanceOf(NotReservationParticipantException.class)
                .extracting("code")
                .isEqualTo("NOT_RESERVATION_PARTICIPANT");

        assertThatThrownBy(() -> reservation.cancel(ClientId.of(Ids.newId()), "No soy el cliente", FIXED_CLOCK))
                .isInstanceOf(NotReservationParticipantException.class);
    }

    private static Reservation newPendingReservation() {
        return Reservation.create(
                CLIENT_ID,
                PROVIDER_1,
                DateRange.of(TODAY, TODAY.plusDays(2)),
                List.of(sampleUnit(PROVIDER_1)),
                FIXED_CLOCK
        );
    }

    private static Reservation reservationInStatus(ReservationStatus status) {
        ReservationUnit unit = sampleUnit(PROVIDER_1);
        return Reservation.reconstruct(
                 com.rentatools.rental.domain.model.reservation.ReservationId.generate(FIXED_CLOCK),
                ReservationCode.generate(),
                CLIENT_ID,
                PROVIDER_1,
                DateRange.of(TODAY, TODAY.plusDays(2)),
                status,
                null,
                List.of(unit),
                FIXED_CLOCK.instant(),
                FIXED_CLOCK.instant(),
                0L
        );
    }

    private static ReservationUnit sampleUnit(ProviderId providerId) {
        return ReservationUnit.create(
                UnitId.of(Ids.newId()),
                providerId,
                Money.of("50.00"),
                Money.of("150.00"),
                Money.of("100.00")
        );
    }
}
