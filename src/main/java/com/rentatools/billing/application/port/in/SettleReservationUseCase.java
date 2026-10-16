package com.rentatools.billing.application.port.in;

import com.rentatools.billing.application.dto.ReservationBillingView;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.shared.domain.Actor;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para liquidar la reserva ({@code POST /settle}):
 * <ul>
 *     <li>Si el borrador {@code SETTLEMENT} tiene líneas, se emite con número secuencial, se aplica
 *         la retención {@code min(total, saldo garantía)}, se devuelve el excedente y la garantía queda liquidada.</li>
 *     <li>Si el borrador no tiene líneas, se elimina y se devuelve toda la garantía.</li>
 *     <li>Si la liquidación queda en saldo 0 de inmediato, publica {@code ReservationSettled}.</li>
 * </ul>
 */
public interface SettleReservationUseCase {

    ReservationBillingView settleReservation(Command command);

    record Command(
            Actor actor,
            UUID reservationId,
            PaymentMethod refundMethod,
            String refundReference
    ) {
        public Command {
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(reservationId, "reservationId must not be null");
            refundMethod = (refundMethod == null) ? PaymentMethod.SIMULATED : refundMethod;
        }

        public Command(Actor actor, UUID reservationId) {
            this(actor, reservationId, PaymentMethod.SIMULATED, "Liquidación de garantía");
        }
    }
}
