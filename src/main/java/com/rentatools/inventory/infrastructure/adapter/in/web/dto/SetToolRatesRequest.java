package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Payload de entrada para definir o reemplazar las tarifas de una herramienta.
 */
public record SetToolRatesRequest(
        @NotEmpty(message = "At least one rate is required")
        @Valid
        List<CreateToolRequest.RateItemRequest> rates
) {
}
