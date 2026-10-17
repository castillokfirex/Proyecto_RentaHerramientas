package com.rentatools.rental.application.mapper;

import com.rentatools.rental.application.dto.ReservationUnitView;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.integration.rental.ReservationCancelled;
import com.rentatools.shared.integration.rental.ReservationConfirmed;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Mapper de aplicación para traducir agregados de {@code rental} a eventos de integración publicados
 * en {@code shared.integration.rental}.
 */
public final class IntegrationEventMapper {

    private IntegrationEventMapper() {
    }

    public static ReservationConfirmed toReservationConfirmed(
            Reservation reservation,
            List<ReservationUnitView> enrichedUnits,
            UUID confirmedBy,
            Clock clock
    ) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        Objects.requireNonNull(enrichedUnits, "enrichedUnits must not be null");
        Objects.requireNonNull(confirmedBy, "confirmedBy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        List<ReservationConfirmed.Line> lines = new ArrayList<>(enrichedUnits.size());
        for (ReservationUnitView u : enrichedUnits) {
            lines.add(new ReservationConfirmed.Line(
                    u.id(),
                    u.toolName() != null ? u.toolName() : "Herramienta",
                    u.serialNumber() != null ? u.serialNumber() : "N/A",
                    u.agreedAmount().amount(),
                    u.depositAmount().amount()
            ));
        }

        return new ReservationConfirmed(
                Ids.newId(clock),
                clock.instant(),
                reservation.id().value(),
                reservation.code().value(),
                reservation.clientId().value(),
                reservation.providerId().value(),
                reservation.dateRange().start(),
                reservation.dateRange().end(),
                confirmedBy,
                lines
        );
    }

    public static ReservationCancelled toReservationCancelled(
            Reservation reservation,
            ReservationStatus previousStatus,
            UUID cancelledBy,
            String reason,
            Clock clock
    ) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        Objects.requireNonNull(previousStatus, "previousStatus must not be null");
        Objects.requireNonNull(cancelledBy, "cancelledBy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        return new ReservationCancelled(
                Ids.newId(clock),
                clock.instant(),
                reservation.id().value(),
                previousStatus.name(),
                cancelledBy,
                reason
        );
    }
}
