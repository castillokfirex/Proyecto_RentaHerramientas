package com.rentatools.rental.domain.model.reservation;

import com.rentatools.shared.domain.Ids;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador único de una unidad asignada a una reserva (reservation_units.id).
 */
public record ReservationUnitId(UUID value) {

    public ReservationUnitId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ReservationUnitId of(UUID value) {
        return new ReservationUnitId(value);
    }

    public static ReservationUnitId generate() {
        return new ReservationUnitId(Ids.newId());
    }

    public static ReservationUnitId generate(Clock clock) {
        return new ReservationUnitId(Ids.newId(clock));
    }
}
