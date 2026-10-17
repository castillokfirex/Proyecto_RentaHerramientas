package com.rentatools.rental.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Métrica del reporte de administración para las herramientas más alquiladas.
 */
public record TopToolReportView(
        UUID toolId,
        String name,
        String brand,
        String model,
        long totalRentals,
        long totalRentalDays
) {
    public TopToolReportView {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
