package com.rentatools.rental.application.dto;

import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vista de lectura completa de una reserva con sus unidades y totales pactados.
 */
public record ReservationView(
        UUID id,
        String code,
        UUID clientId,
        UUID providerId,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status,
        String statusReason,
        List<ReservationUnitView> units,
        Money totalAgreedAmount,
        Money totalDepositAmount,
        Instant createdAt,
        Instant updatedAt
) {

    public ReservationView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(units, "units must not be null");
        units = List.copyOf(units);
        Objects.requireNonNull(totalAgreedAmount, "totalAgreedAmount must not be null");
        Objects.requireNonNull(totalDepositAmount, "totalDepositAmount must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
