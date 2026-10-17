package com.rentatools.rental.application.dto;

import com.rentatools.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * Vista de lectura de una unidad asignada a una reserva.
 */
public record ReservationUnitView(
        UUID id,
        UUID unitId,
        UUID toolId,
        String toolName,
        String serialNumber,
        Money dailyRate,
        Money agreedAmount,
        Money depositAmount
) {

    public ReservationUnitView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(unitId, "unitId must not be null");
        Objects.requireNonNull(dailyRate, "dailyRate must not be null");
        Objects.requireNonNull(agreedAmount, "agreedAmount must not be null");
        Objects.requireNonNull(depositAmount, "depositAmount must not be null");
    }
}
