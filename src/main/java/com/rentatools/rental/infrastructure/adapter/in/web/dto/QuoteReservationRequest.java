package com.rentatools.rental.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload de entrada para cotizar una reserva ({@code POST /api/v1/reservations/quote}).
 */
public record QuoteReservationRequest(
        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @NotEmpty(message = "items must not be empty")
        @Valid
        List<ItemRequest> items
) {

    public record ItemRequest(
            @NotNull(message = "toolId is required")
            UUID toolId,

            @Min(value = 1, message = "quantity must be >= 1")
            int quantity
    ) {
    }
}
