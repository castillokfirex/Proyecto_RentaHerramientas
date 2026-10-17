package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import com.rentatools.inventory.domain.model.tool.RatePeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Payload de entrada para crear una herramienta en estado {@code DRAFT}.
 */
public record CreateToolRequest(
        @NotBlank(message = "Tool name is required")
        @Size(max = 150, message = "Tool name must not exceed 150 characters")
        String name,
        @Size(max = 80, message = "Brand must not exceed 80 characters")
        String brand,
        @Size(max = 80, message = "Model must not exceed 80 characters")
        String model,
        String description,
        @NotNull(message = "depositAmount is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "depositAmount must be >= 0")
        BigDecimal depositAmount,
        Set<UUID> categoryIds,
        @Valid
        List<RateItemRequest> rates
) {
    public record RateItemRequest(
            @NotNull(message = "period is required")
            RatePeriod period,
            @NotNull(message = "price is required")
            @DecimalMin(value = "0.01", inclusive = true, message = "price must be > 0")
            BigDecimal price
    ) {
    }
}
