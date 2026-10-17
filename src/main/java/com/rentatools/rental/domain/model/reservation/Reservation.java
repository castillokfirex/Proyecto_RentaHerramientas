package com.rentatools.rental.domain.model.reservation;

import com.rentatools.rental.domain.event.AllUnitsDelivered;
import com.rentatools.rental.domain.event.AllUnitsReturned;
import com.rentatools.rental.domain.event.ReservationCancelledEvt;
import com.rentatools.rental.domain.event.ReservationConfirmedEvt;
import com.rentatools.rental.domain.event.ReservationCreated;
import com.rentatools.rental.domain.exception.InvalidReservationTransitionException;
import com.rentatools.rental.domain.exception.MixedProvidersException;
import com.rentatools.rental.domain.exception.NotReservationParticipantException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.AggregateRoot;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Raíz de agregado que modela una reserva de alquiler (`reservations` y `reservation_units`).
 *
 * <p>Invariantes principales:</p>
 * <ul>
 *   <li>Código Crockford Base32 único de 10 caracteres.</li>
 *   <li>Un único proveedor por reserva (no se mezclan herramientas de distintos proveedores).</li>
 *   <li>El cliente y el proveedor deben ser usuarios distintos.</li>
 *   <li>Rango de fechas inclusivo con {@code start_date >= hoy} al crear la reserva.</li>
 *   <li>Al menos una unidad física reservada, sin repetir unidad dentro de la misma reserva,
 *       con tarifas históricas congeladas.</li>
 *   <li>Máquina de estados estricta según {@code PLAN.md} §3.2.</li>
 * </ul>
 */
public final class Reservation extends AggregateRoot {

    private static final int MAX_STATUS_REASON_LENGTH = 300;

    private final ReservationId id;
    private final ReservationCode code;
    private final ClientId clientId;
    private final ProviderId providerId;
    private final DateRange dateRange;
    private final List<ReservationUnit> units;
    private final Instant createdAt;
    private ReservationStatus status;
    private String statusReason;
    private Instant updatedAt;

    private final Set<ReservationUnitId> deliveredUnitIds = new LinkedHashSet<>();
    private final Set<ReservationUnitId> returnedUnitIds = new LinkedHashSet<>();

    private Reservation(
            ReservationId id,
            ReservationCode code,
            ClientId clientId,
            ProviderId providerId,
            DateRange dateRange,
            ReservationStatus status,
            String statusReason,
            List<ReservationUnit> units,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        super(version);
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.dateRange = Objects.requireNonNull(dateRange, "dateRange must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.statusReason = normalizeReason(statusReason);
        this.units = validateAndCopyUnits(units, providerId);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (clientId.value().equals(providerId.value())) {
            throw new BusinessRuleViolation(
                    "SELF_RESERVATION_NOT_ALLOWED",
                    "Client and provider cannot be the same user"
            );
        }
    }

    /**
     * Crea una nueva reserva en estado {@link ReservationStatus#PENDING} validando que {@code start_date >= hoy}
     * según el {@link Clock} inyectado y que todas las unidades pertenezcan al mismo proveedor.
     */
    public static Reservation create(
            ReservationId id,
            ReservationCode code,
            ClientId clientId,
            ProviderId providerId,
            DateRange dateRange,
            List<ReservationUnit> units,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(dateRange, "dateRange must not be null");
        LocalDate today = LocalDate.now(clock);
        if (dateRange.start().isBefore(today)) {
            throw new BusinessRuleViolation(
                    "PAST_START_DATE",
                    "Reservation start date (" + dateRange.start() + ") cannot be before today (" + today + ")"
            );
        }

        Instant now = clock.instant();
        Reservation reservation = new Reservation(
                id,
                code,
                clientId,
                providerId,
                dateRange,
                ReservationStatus.PENDING,
                null,
                units,
                now,
                now,
                0L
        );
        reservation.registerEvent(new ReservationCreated(
                Ids.newId(clock),
                now,
                reservation.id,
                reservation.code,
                reservation.clientId,
                reservation.providerId,
                reservation.dateRange
        ));
        return reservation;
    }

    /**
     * Crea una nueva reserva generando automáticamente el {@link ReservationId} y el {@link ReservationCode}.
     */
    public static Reservation create(
            ClientId clientId,
            ProviderId providerId,
            DateRange dateRange,
            List<ReservationUnit> units,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "clock must not be null");
        return create(
                ReservationId.generate(clock),
                ReservationCode.generate(),
                clientId,
                providerId,
                dateRange,
                units,
                clock
        );
    }

    /**
     * Crea una nueva reserva deduciendo el {@link ProviderId} a partir de las unidades solicitadas.
     * Lanza {@link MixedProvidersException} si las unidades pertenecen a distintos proveedores.
     */
    public static Reservation create(
            ClientId clientId,
            DateRange dateRange,
            List<ReservationUnit> units,
            Clock clock
    ) {
        Objects.requireNonNull(units, "units must not be null");
        if (units.isEmpty()) {
            throw new BusinessRuleViolation("EMPTY_RESERVATION_UNITS", "Reservation must have at least one unit");
        }
        Set<ProviderId> providers = new LinkedHashSet<>();
        for (ReservationUnit unit : units) {
            Objects.requireNonNull(unit, "unit must not be null");
            if (unit.providerId() != null) {
                providers.add(unit.providerId());
            }
        }
        ProviderId singleProvider = ensureSingleProvider(providers);
        return create(clientId, singleProvider, dateRange, units, clock);
    }

    /**
     * Reconstruye una reserva desde la capa de persistencia sin emitir eventos ni validar fecha actual.
     */
    public static Reservation reconstruct(
            ReservationId id,
            ReservationCode code,
            ClientId clientId,
            ProviderId providerId,
            DateRange dateRange,
            ReservationStatus status,
            String statusReason,
            List<ReservationUnit> units,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new Reservation(
                id,
                code,
                clientId,
                providerId,
                dateRange,
                status,
                statusReason,
                units,
                createdAt,
                updatedAt,
                version
        );
    }

    /**
     * Verifica que una colección de proveedores contenga exactamente un único proveedor.
     * Si hay más de uno, lanza {@link MixedProvidersException}.
     */
    public static ProviderId ensureSingleProvider(Collection<ProviderId> providerIds) {
        Objects.requireNonNull(providerIds, "providerIds must not be null");
        Set<ProviderId> distinct = new LinkedHashSet<>(providerIds);
        if (distinct.isEmpty()) {
            throw new BusinessRuleViolation("MISSING_PROVIDER", "At least one provider must be specified");
        }
        if (distinct.size() > 1) {
            throw new MixedProvidersException();
        }
        return distinct.iterator().next();
    }

    /**
     * Transición: {@code PENDING -> CONFIRMED} (aprobada por el proveedor).
     */
    public void confirm(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (this.status != ReservationStatus.PENDING) {
            throw new InvalidReservationTransitionException(this.status, ReservationStatus.CONFIRMED);
        }
        Instant now = clock.instant();
        this.status = ReservationStatus.CONFIRMED;
        this.updatedAt = now;
        registerEvent(new ReservationConfirmedEvt(
                Ids.newId(clock),
                now,
                this.id,
                this.code,
                this.clientId,
                this.providerId,
                this.dateRange
        ));
    }

    /**
     * Transición: {@code PENDING -> CONFIRMED} validando que quien confirma sea el proveedor dueño.
     */
    public void confirm(ProviderId actorProviderId, Clock clock) {
        assertOwnedByProvider(actorProviderId);
        confirm(clock);
    }

    /**
     * Transición: {@code PENDING -> CONFIRMED} validando el {@link Actor}.
     */
    public void confirm(Actor actor, Clock clock) {
        assertProviderActor(actor);
        confirm(clock);
    }

    /**
     * Transición: {@code PENDING -> REJECTED} (rechazada por el proveedor).
     */
    public void reject(String reason, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (this.status != ReservationStatus.PENDING) {
            throw new InvalidReservationTransitionException(this.status, ReservationStatus.REJECTED);
        }
        this.status = ReservationStatus.REJECTED;
        this.statusReason = normalizeReason(reason);
        this.updatedAt = clock.instant();
    }

    /**
     * Transición: {@code PENDING -> REJECTED} validando que quien rechaza sea el proveedor dueño.
     */
    public void reject(ProviderId actorProviderId, String reason, Clock clock) {
        assertOwnedByProvider(actorProviderId);
        reject(reason, clock);
    }

    /**
     * Transición: {@code PENDING -> REJECTED} validando el {@link Actor}.
     */
    public void reject(Actor actor, String reason, Clock clock) {
        assertProviderActor(actor);
        reject(reason, clock);
    }

    /**
     * Transición: {@code PENDING / CONFIRMED / READY_FOR_PICKUP -> CANCELLED} (cancelada por el cliente).
     */
    public void cancel(String reason, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (!this.status.isCancellableByClient()) {
            throw new InvalidReservationTransitionException(this.status, ReservationStatus.CANCELLED);
        }
        ReservationStatus previousStatus = this.status;
        Instant now = clock.instant();
        this.status = ReservationStatus.CANCELLED;
        this.statusReason = normalizeReason(reason);
        this.updatedAt = now;
        registerEvent(new ReservationCancelledEvt(
                Ids.newId(clock),
                now,
                this.id,
                previousStatus,
                this.statusReason
        ));
    }

    /**
     * Transición: {@code PENDING / CONFIRMED / READY_FOR_PICKUP -> CANCELLED} validando que sea el cliente titular.
     */
    public void cancel(ClientId actorClientId, String reason, Clock clock) {
        assertOwnedByClient(actorClientId);
        cancel(reason, clock);
    }

    /**
     * Transición: {@code PENDING / CONFIRMED / READY_FOR_PICKUP -> CANCELLED} validando el {@link Actor}.
     */
    public void cancel(Actor actor, String reason, Clock clock) {
        assertClientActor(actor);
        cancel(reason, clock);
    }

    /**
     * Transición: {@code CONFIRMED -> READY_FOR_PICKUP} (notificada por evento de facturación).
     */
    public void markClearedForDelivery(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (this.status != ReservationStatus.CONFIRMED) {
            throw new InvalidReservationTransitionException(this.status, ReservationStatus.READY_FOR_PICKUP);
        }
        this.status = ReservationStatus.READY_FOR_PICKUP;
        this.updatedAt = clock.instant();
    }

    /**
     * Alias de {@link #markClearedForDelivery(Clock)}.
     */
    public void markReadyForPickup(Clock clock) {
        markClearedForDelivery(clock);
    }

    /**
     * Registra la entrega (inspección DELIVERY) de una unidad de la reserva.
     * Cuando se han entregado todas las unidades de la reserva, transiciona {@code READY_FOR_PICKUP -> ACTIVE}
     * y registra {@link AllUnitsDelivered}.
     */
    public void registerUnitDelivery(ReservationUnitId reservationUnitId, Clock clock) {
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (this.status != ReservationStatus.READY_FOR_PICKUP) {
            throw new InvalidReservationTransitionException(this.status, ReservationStatus.ACTIVE);
        }
        assertContainsReservationUnit(reservationUnitId);
        this.deliveredUnitIds.add(reservationUnitId);
        this.updatedAt = clock.instant();

        if (this.deliveredUnitIds.size() == this.units.size()) {
            this.status = ReservationStatus.ACTIVE;
            registerEvent(new AllUnitsDelivered(
                    Ids.newId(clock),
                    this.updatedAt,
                    this.id
            ));
        }
    }

    /**
     * Transición directa: {@code READY_FOR_PICKUP -> ACTIVE} cuando todas las unidades han sido entregadas.
     */
    public void markAllUnitsDelivered(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (this.status != ReservationStatus.READY_FOR_PICKUP) {
            throw new InvalidReservationTransitionException(this.status, ReservationStatus.ACTIVE);
        }
        for (ReservationUnit unit : this.units) {
            this.deliveredUnitIds.add(unit.id());
        }
        Instant now = clock.instant();
        this.status = ReservationStatus.ACTIVE;
        this.updatedAt = now;
        registerEvent(new AllUnitsDelivered(
                Ids.newId(clock),
                now,
                this.id
        ));
    }

    /**
     * Alias de {@link #markAllUnitsDelivered(Clock)}.
     */
    public void activate(Clock clock) {
        markAllUnitsDelivered(clock);
    }

    /**
     * Registra la devolución (inspección RETURN) de una unidad de la reserva.
     * Cuando se han devuelto todas las unidades de la reserva, transiciona {@code ACTIVE -> RETURNED}
     * y registra {@link AllUnitsReturned}.
     */
    public void registerUnitReturn(ReservationUnitId reservationUnitId, Clock clock) {
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (this.status != ReservationStatus.ACTIVE) {
            throw new InvalidReservationTransitionException(this.status, ReservationStatus.RETURNED);
        }
        assertContainsReservationUnit(reservationUnitId);
        this.returnedUnitIds.add(reservationUnitId);
        this.updatedAt = clock.instant();

        if (this.returnedUnitIds.size() == this.units.size()) {
            this.status = ReservationStatus.RETURNED;
            registerEvent(new AllUnitsReturned(
                    Ids.newId(clock),
                    this.updatedAt,
                    this.id
            ));
        }
    }

    /**
     * Transición directa: {@code ACTIVE -> RETURNED} cuando todas las unidades han sido devueltas.
     */
    public void markAllUnitsReturned(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (this.status != ReservationStatus.ACTIVE) {
            throw new InvalidReservationTransitionException(this.status, ReservationStatus.RETURNED);
        }
        for (ReservationUnit unit : this.units) {
            this.returnedUnitIds.add(unit.id());
        }
        Instant now = clock.instant();
        this.status = ReservationStatus.RETURNED;
        this.updatedAt = now;
        registerEvent(new AllUnitsReturned(
                Ids.newId(clock),
                now,
                this.id
        ));
    }

    /**
     * Alias de {@link #markAllUnitsReturned(Clock)}.
     */
    public void markReturned(Clock clock) {
        markAllUnitsReturned(clock);
    }

    /**
     * Transición: {@code RETURNED -> CLOSED} (notificada por evento de liquidación completada).
     */
    public void close(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (this.status != ReservationStatus.RETURNED) {
            throw new InvalidReservationTransitionException(this.status, ReservationStatus.CLOSED);
        }
        this.status = ReservationStatus.CLOSED;
        this.updatedAt = clock.instant();
    }

    /**
     * Alias de {@link #close(Clock)}.
     */
    public void markClosed(Clock clock) {
        close(clock);
    }

    public void recordDeliveredUnits(Collection<ReservationUnitId> unitIds) {
        if (unitIds != null) {
            this.deliveredUnitIds.addAll(unitIds);
        }
    }

    public void recordReturnedUnits(Collection<ReservationUnitId> unitIds) {
        if (unitIds != null) {
            this.returnedUnitIds.addAll(unitIds);
        }
    }

    public Set<ReservationUnitId> deliveredUnitIds() {
        return Collections.unmodifiableSet(deliveredUnitIds);
    }

    public Set<ReservationUnitId> returnedUnitIds() {
        return Collections.unmodifiableSet(returnedUnitIds);
    }

    /**
     * Verifica que el actor sea partícipe de la reserva (cliente, proveedor o ADMIN).
     */
    public void assertParticipant(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (actor.is(ActorRole.ADMIN)) {
            return;
        }
        if (!isParticipant(actor.userId())) {
            throw new NotReservationParticipantException(this.id.value(), actor.userId());
        }
    }

    public boolean isParticipant(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return this.clientId.value().equals(userId) || this.providerId.value().equals(userId);
    }

    public void assertOwnedByProvider(ProviderId candidateProviderId) {
        Objects.requireNonNull(candidateProviderId, "candidateProviderId must not be null");
        if (!this.providerId.equals(candidateProviderId)) {
            throw new NotReservationParticipantException(this.id.value(), candidateProviderId.value());
        }
    }

    public void assertOwnedByClient(ClientId candidateClientId) {
        Objects.requireNonNull(candidateClientId, "candidateClientId must not be null");
        if (!this.clientId.equals(candidateClientId)) {
            throw new NotReservationParticipantException(this.id.value(), candidateClientId.value());
        }
    }

    public Optional<ReservationUnit> findReservationUnit(ReservationUnitId reservationUnitId) {
        Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
        for (ReservationUnit unit : this.units) {
            if (unit.id().equals(reservationUnitId)) {
                return Optional.of(unit);
            }
        }
        return Optional.empty();
    }

    public Money totalAgreedAmount() {
        Money total = Money.ZERO;
        for (ReservationUnit unit : this.units) {
            total = total.plus(unit.agreedAmount());
        }
        return total;
    }

    public Money totalDepositAmount() {
        Money total = Money.ZERO;
        for (ReservationUnit unit : this.units) {
            total = total.plus(unit.depositAmount());
        }
        return total;
    }

    public ReservationId id() {
        return id;
    }

    public ReservationCode code() {
        return code;
    }

    public ClientId clientId() {
        return clientId;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public DateRange dateRange() {
        return dateRange;
    }

    public ReservationStatus status() {
        return status;
    }

    public String statusReason() {
        return statusReason;
    }

    public List<ReservationUnit> units() {
        return units;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void assertProviderActor(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (!actor.is(ActorRole.ADMIN) && !this.providerId.value().equals(actor.userId())) {
            throw new NotReservationParticipantException(this.id.value(), actor.userId());
        }
    }

    private void assertClientActor(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (!actor.is(ActorRole.ADMIN) && !this.clientId.value().equals(actor.userId())) {
            throw new NotReservationParticipantException(this.id.value(), actor.userId());
        }
    }

    private void assertContainsReservationUnit(ReservationUnitId reservationUnitId) {
        if (findReservationUnit(reservationUnitId).isEmpty()) {
            throw new BusinessRuleViolation(
                    "RESERVATION_UNIT_NOT_FOUND",
                    "Reservation unit " + reservationUnitId.value() + " does not belong to reservation " + id.value()
            );
        }
    }

    private static List<ReservationUnit> validateAndCopyUnits(
            List<ReservationUnit> units,
            ProviderId reservationProviderId
    ) {
        Objects.requireNonNull(units, "units must not be null");
        if (units.isEmpty()) {
            throw new BusinessRuleViolation("EMPTY_RESERVATION_UNITS", "Reservation must have at least one unit");
        }
        Set<UnitId> seenUnits = new HashSet<>();
        for (ReservationUnit unit : units) {
            Objects.requireNonNull(unit, "unit must not be null");
            if (unit.providerId() != null && !unit.providerId().equals(reservationProviderId)) {
                throw new MixedProvidersException();
            }
            if (!seenUnits.add(unit.unitId())) {
                throw new BusinessRuleViolation(
                        "DUPLICATE_RESERVATION_UNIT",
                        "Unit " + unit.unitId().value() + " is duplicated in the same reservation"
                );
            }
        }
        return List.copyOf(units);
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_STATUS_REASON_LENGTH) {
            throw new BusinessRuleViolation(
                    "STATUS_REASON_TOO_LONG",
                    "statusReason cannot exceed " + MAX_STATUS_REASON_LENGTH + " characters"
            );
        }
        return trimmed;
    }
}
