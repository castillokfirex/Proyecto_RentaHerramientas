package com.rentatools.rental.application.dto;

import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DateRange;
import java.util.List;
import java.util.Objects;

/**
 * Comando para crear y persistir una nueva reserva de alquiler.
 */
public record CreateReservationCommand(
        DateRange dateRange,
        List<RequestedToolItem> items
) {

    public CreateReservationCommand {
        Objects.requireNonNull(dateRange, "dateRange must not be null");
        Objects.requireNonNull(items, "items must not be null");
        if (items.isEmpty()) {
            throw new BusinessRuleViolation("EMPTY_RESERVATION_ITEMS", "At least one tool item must be requested");
        }
        items = List.copyOf(items);
    }
}
