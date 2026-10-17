package com.rentatools.rental.application.mapper;

import com.rentatools.rental.application.dto.ReservationUnitView;
import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.application.port.out.ToolCatalogPort.RentableToolDescriptor;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort.OperationalUnitDescriptor;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Mapper de aplicación que convierte el agregado {@link Reservation} a su vista de lectura
 * {@link ReservationView}, enriqueciendo cada unidad con nombre de herramienta y número de serie.
 */
public final class ReservationViewMapper {

    private ReservationViewMapper() {
    }

    public static ReservationView toView(
            Reservation reservation,
            UnitInventoryPort unitInventoryPort,
            ToolCatalogPort toolCatalogPort
    ) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        Objects.requireNonNull(unitInventoryPort, "unitInventoryPort must not be null");
        Objects.requireNonNull(toolCatalogPort, "toolCatalogPort must not be null");

        Map<UUID, String> toolNamesByToolId = new HashMap<>();
        List<ReservationUnitView> unitViews = new ArrayList<>(reservation.units().size());

        for (ReservationUnit ru : reservation.units()) {
            UUID unitId = ru.unitId().value();
            UUID toolId = null;
            String serialNumber = "N/A";
            String toolName = "Herramienta";

            Optional<OperationalUnitDescriptor> unitOpt = unitInventoryPort.findUnit(unitId);
            if (unitOpt.isPresent()) {
                OperationalUnitDescriptor unitDesc = unitOpt.get();
                toolId = unitDesc.toolId();
                serialNumber = unitDesc.serialNumber();
                toolName = toolNamesByToolId.computeIfAbsent(toolId, tid ->
                        toolCatalogPort.findRentableTool(tid)
                                .map(RentableToolDescriptor::name)
                                .orElse("Herramienta")
                );
            }

            unitViews.add(new ReservationUnitView(
                    ru.id().value(),
                    unitId,
                    toolId,
                    toolName,
                    serialNumber,
                    ru.dailyRate(),
                    ru.agreedAmount(),
                    ru.depositAmount()
            ));
        }

        return new ReservationView(
                reservation.id().value(),
                reservation.code().value(),
                reservation.clientId().value(),
                reservation.providerId().value(),
                reservation.dateRange().start(),
                reservation.dateRange().end(),
                reservation.status(),
                reservation.statusReason(),
                unitViews,
                reservation.totalAgreedAmount(),
                reservation.totalDepositAmount(),
                reservation.createdAt(),
                reservation.updatedAt()
        );
    }
}
