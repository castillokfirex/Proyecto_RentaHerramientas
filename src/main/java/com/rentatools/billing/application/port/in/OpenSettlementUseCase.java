package com.rentatools.billing.application.port.in;

import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.shared.domain.Money;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso desencadenado al devolverse la reserva ({@code ReservationReturned}):
 * crea la factura {@code SETTLEMENT} en borrador agregando líneas {@code LATE_FEE}
 * para las unidades devueltas con retraso.
 */
public interface OpenSettlementUseCase {

    InvoiceView openSettlement(Command command);

    record Command(
            UUID reservationId,
            List<ReturnedUnitCharge> units
    ) {
        public Command {
            Objects.requireNonNull(reservationId, "reservationId must not be null");
            Objects.requireNonNull(units, "units must not be null");
            units = List.copyOf(units);
        }
    }

    record ReturnedUnitCharge(
            UUID reservationUnitId,
            String toolName,
            int lateDays,
            Money dailyRate
    ) {
        public ReturnedUnitCharge {
            Objects.requireNonNull(reservationUnitId, "reservationUnitId must not be null");
            Objects.requireNonNull(dailyRate, "dailyRate must not be null");
        }
    }
}
