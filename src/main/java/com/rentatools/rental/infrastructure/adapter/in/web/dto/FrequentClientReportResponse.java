package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.application.dto.FrequentClientReportView;
import java.util.UUID;

public record FrequentClientReportResponse(
        UUID clientId,
        String fullName,
        String email,
        long totalReservations,
        long confirmedReservations,
        long closedReservations,
        long activeReservations
) {
    public static FrequentClientReportResponse from(FrequentClientReportView view) {
        return new FrequentClientReportResponse(
                view.clientId(),
                view.fullName(),
                view.email(),
                view.totalReservations(),
                view.confirmedReservations(),
                view.closedReservations(),
                view.activeReservations()
        );
    }
}
