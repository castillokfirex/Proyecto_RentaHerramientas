package com.rentatools.billing.application.port.in;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso desencadenado al cancelarse una reserva ({@code ReservationCancelled}):
 * anula la factura {@code RENTAL}, registra el reembolso de lo pagado y devuelve la garantía cobrada,
 * dejando la garantía liquidada.
 */
public interface CancelReservationBillingUseCase {

    void cancelReservationBilling(Command command);

    record Command(
            UUID reservationId,
            UUID cancelledBy,
            String reason
    ) {
        public Command {
            Objects.requireNonNull(reservationId, "reservationId must not be null");
            Objects.requireNonNull(cancelledBy, "cancelledBy must not be null");
        }
    }
}
