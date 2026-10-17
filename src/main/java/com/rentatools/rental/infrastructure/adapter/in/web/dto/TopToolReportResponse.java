package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.application.dto.TopToolReportView;
import java.util.UUID;

public record TopToolReportResponse(
        UUID toolId,
        String name,
        String brand,
        String model,
        long totalRentals,
        long totalRentalDays
) {
    public static TopToolReportResponse from(TopToolReportView view) {
        return new TopToolReportResponse(
                view.toolId(),
                view.name(),
                view.brand(),
                view.model(),
                view.totalRentals(),
                view.totalRentalDays()
        );
    }
}
