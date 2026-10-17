package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import com.rentatools.rental.application.dto.ReservationUnitView;
import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta JSON con el detalle de una reserva y sus unidades físicas asignadas.
 */
public record ReservationResponse(
        UUID id,
        String code,
        UUID clientId,
        UUID providerId,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status,
        String statusReason,
        List<UnitResponse> units,
        BigDecimal totalAgreedAmount,
        BigDecimal totalDepositAmount,
        Instant createdAt,
        Instant updatedAt
) {

    public record UnitResponse(
            UUID id,
            UUID unitId,
            UUID toolId,
            String toolName,
            String serialNumber,
            BigDecimal dailyRate,
            BigDecimal agreedAmount,
            BigDecimal depositAmount
    ) {
        public static UnitResponse from(ReservationUnitView view) {
            return new UnitResponse(
                    view.id(),
                    view.unitId(),
                    view.toolId(),
                    view.toolName(),
                    view.serialNumber(),
                    view.dailyRate().amount(),
                    view.agreedAmount().amount(),
                    view.depositAmount().amount()
            );
        }
    }

    public static ReservationResponse from(ReservationView view) {
        return new ReservationResponse(
                view.id(),
                view.code(),
                view.clientId(),
                view.providerId(),
                view.startDate(),
                view.endDate(),
                view.status(),
                view.statusReason(),
                view.units().stream().map(UnitResponse::from).toList(),
                view.totalAgreedAmount().amount(),
                view.totalDepositAmount().amount(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
