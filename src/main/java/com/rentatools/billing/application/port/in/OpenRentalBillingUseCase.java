package com.rentatools.billing.application.port.in;

import com.rentatools.billing.application.dto.ReservationBillingView;
import com.rentatools.shared.domain.Money;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso desencadenado al confirmarse una reserva ({@code ReservationConfirmed}):
 * crea la factura {@code RENTAL} emitida con número secuencial y abre la garantía {@code SecurityDeposit}.
 */
public interface OpenRentalBillingUseCase {

    ReservationBillingView openRentalBilling(Command command);

    record Command(
            UUID reservationId,
            List<UnitRentalCharge> units
    ) {
        public Command {
            Objects.requireNonNull(reservationId, "reservationId must not be null");
            Objects.requireNonNull(units, "units must not be null");
            units = List.copyOf(units);
        }
    }

    record UnitRentalCharge(
            UUID reservationUnitId,
            String toolName,
            String serialNumber,
            Money agreedAmount,
            Money depositAmount
    ) {
        public UnitRentalCharge {
            Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
            Objects.requireNonNull(agreedAmount, "agreedAmount must not be null");
            Objects.requireNonNull(depositAmount, "depositAmount must not be null");
        }
    }
}
