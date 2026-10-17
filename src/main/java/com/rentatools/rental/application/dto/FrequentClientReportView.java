package com.rentatools.rental.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Métrica del reporte de clientes frecuentes ordenada por cantidad de reservas confirmadas y cerradas.
 */
public record FrequentClientReportView(
        UUID clientId,
        String fullName,
        String email,
        long totalReservations,
        long confirmedReservations,
        long closedReservations,
        long activeReservations
) {
    public FrequentClientReportView {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
    }
}
