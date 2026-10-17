package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.application.dto.ToolStatusReportView;

public record ToolStatusReportResponse(
        long totalUnits,
        long availableUnits,
        long maintenanceUnits,
        long retiredUnits,
        long activeOccupiedUnits
) {
    public static ToolStatusReportResponse from(ToolStatusReportView view) {
        return new ToolStatusReportResponse(
                view.totalUnits(),
                view.availableUnits(),
                view.maintenanceUnits(),
                view.retiredUnits(),
                view.activeOccupiedUnits()
        );
    }
}
