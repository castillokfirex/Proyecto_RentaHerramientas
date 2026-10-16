package com.rentatools.billing.application.port.in;

import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para que el proveedor dueño (o admin) agregue un cargo por daño ({@code DAMAGE})
 * a una factura {@code SETTLEMENT} en borrador.
 */
public interface AddDamageChargeUseCase {

    InvoiceView addDamageCharge(Command command);

    record Command(
            Actor actor,
            UUID invoiceId,
            UUID reservationUnitId,
            String description,
            Money amount
    ) {
        public Command {
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(invoiceId, "invoiceId must not be null");
            Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
        }
    }
}
