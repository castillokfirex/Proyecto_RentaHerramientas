package com.rentatools.rental.application.dto;

import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DateRange;
import java.util.List;
import java.util.Objects;

/**
 * Comando para cotizar una reserva sin persistirla.
 */
public record QuoteReservationCommand(
        DateRange dateRange,
        List<RequestedToolItem> items
) {

    public QuoteReservationCommand {
        Objects.requireNonNull(dateRange, "dateRange must not be null");
        Objects.requireNonNull(items, "items must not be null");
        if (items.isEmpty()) {
            throw new BusinessRuleViolation("EMPTY_RESERVATION_ITEMS", "At least one tool item must be requested");
        }
        items = List.copyOf(items);
    }
}
