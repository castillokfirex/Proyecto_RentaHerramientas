package com.rentatools.rental.domain.model.inspection;

import com.rentatools.shared.domain.Ids;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador único de una inspección (`inspections.id`).
 */
public record InspectionId(UUID value) {

    public InspectionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static InspectionId of(UUID value) {
        return new InspectionId(value);
    }

    public static InspectionId generate() {
        return new InspectionId(Ids.newId());
    }

    public static InspectionId generate(Clock clock) {
        return new InspectionId(Ids.newId(clock));
    }
}
