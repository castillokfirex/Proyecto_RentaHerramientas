package com.rentatools.billing.application.port.out;

import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida hacia el contexto {@code rental} para consultar los partícipes (cliente y proveedor)
 * de una reserva y verificar las reglas de autorización de negocio ({@code PLAN.md} §4.2).
 */
public interface ReservationParticipantsPort {

    Optional<Participants> findByReservationId(UUID reservationId);

    default Participants getRequiredByReservationId(UUID reservationId) {
        return findByReservationId(reservationId)
                .orElseThrow(() -> new NotFoundException(
                        "RESERVATION_NOT_FOUND",
                        "Reservation not found: " + reservationId
                ));
    }

    /**
     * Información de los partícipes de una reserva en el lenguaje ubicuo de {@code billing}.
     */
    record Participants(
            UUID reservationId,
            String reservationCode,
            UUID clientId,
            UUID providerId
    ) {
        public Participants {
            Objects.requireNonNull(reservationId, "reservationId must not be null");
            Objects.requireNonNull(reservationCode, "reservationCode must not be null");
            Objects.requireNonNull(clientId, "clientId must not be null");
            Objects.requireNonNull(providerId, "providerId must not be null");
        }

        public boolean isClient(Actor actor) {
            return actor != null && actor.is(ActorRole.CLIENTE) && clientId.equals(actor.userId());
        }

        public boolean isProvider(Actor actor) {
            return actor != null && actor.is(ActorRole.PROVEEDOR) && providerId.equals(actor.userId());
        }

        public boolean isAdmin(Actor actor) {
            return actor != null && actor.is(ActorRole.ADMIN);
        }

        public boolean isParticipantOrAdmin(Actor actor) {
            return isAdmin(actor) || isClient(actor) || isProvider(actor);
        }

        public void assertParticipantOrAdmin(Actor actor) {
            if (!isParticipantOrAdmin(actor)) {
                throw new ForbiddenException(
                        "BILLING_NOT_PARTICIPANT",
                        "Actor is not a participant of reservation " + reservationId
                );
            }
        }

        public void assertProviderOwnerOrAdmin(Actor actor) {
            if (!isAdmin(actor) && !isProvider(actor)) {
                throw new ForbiddenException(
                        "BILLING_NOT_PROVIDER_OWNER",
                        "Only the reservation provider or an admin can perform this billing operation"
                );
            }
        }

        /**
         * Verifica que el actor sea partícipe y utilice un medio de pago permitido para su rol (§4.2):
         * <ul>
         *     <li>{@code ADMIN}: cualquier medio.</li>
         *     <li>{@code PROVEEDOR} dueño: cualquier medio.</li>
         *     <li>{@code CLIENTE} dueño: únicamente {@link PaymentMethod#SIMULATED}.</li>
         * </ul>
         */
        public void assertCanRecordFinancialMovement(Actor actor, PaymentMethod method) {
            assertParticipantOrAdmin(actor);
            if (!isAdmin(actor) && !isProvider(actor) && isClient(actor) && method != PaymentMethod.SIMULATED) {
                throw new ForbiddenException(
                        "CLIENT_PAYMENT_METHOD_FORBIDDEN",
                        "Clients can only record SIMULATED payments or deposit collections"
                );
            }
        }
    }
}
