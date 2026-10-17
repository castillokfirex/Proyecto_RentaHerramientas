package com.rentatools.rental.domain.model.reservation;

import com.rentatools.shared.domain.Ids;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador único de una reserva.
 */
public record ReservationId(UUID value) {

    public ReservationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ReservationId of(UUID value) {
        return new ReservationId(value);
    }

    public static ReservationId generate() {
        return new ReservationId(Ids.newId());
    }

    public static ReservationId generate(Clock clock) {
        return new ReservationId(Ids.newId(clock));
    }
}
