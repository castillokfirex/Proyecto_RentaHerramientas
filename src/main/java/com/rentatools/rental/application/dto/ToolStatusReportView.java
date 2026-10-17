package com.rentatools.rental.application.dto;

/**
 * Métrica del estado consolidado de unidades de inventario y ocupación activa en alquiler.
 */
public record ToolStatusReportView(
        long totalUnits,
        long availableUnits,
        long maintenanceUnits,
        long retiredUnits,
        long activeOccupiedUnits
) {
}
