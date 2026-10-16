package com.rentatools.billing.application.port.in;

import com.rentatools.billing.application.dto.SecurityDepositView;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para registrar un cobro ({@code COLLECTION}) en el depósito de garantía de una reserva
 * y verificar si procede publicar {@code ReservationClearedForDelivery}.
 */
public interface CollectDepositUseCase {

    SecurityDepositView collectDeposit(Command command);

    record Command(
            Actor actor,
            UUID reservationId,
            Money amount,
            PaymentMethod method,
            String reference
    ) {
        public Command {
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(reservationId, "reservationId must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
            Objects.requireNonNull(method, "method must not be null");
        }
    }
}
