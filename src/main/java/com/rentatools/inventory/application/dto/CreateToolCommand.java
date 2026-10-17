package com.rentatools.inventory.application.dto;

import com.rentatools.inventory.domain.model.tool.ToolRate;
import com.rentatools.shared.domain.Money;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Comando para registrar una nueva herramienta en estado {@code DRAFT}.
 */
public record CreateToolCommand(
        String name,
        String brand,
        String model,
        String description,
        Money depositAmount,
        Set<UUID> categoryIds,
        List<ToolRate> rates
) {
    public CreateToolCommand {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(depositAmount, "depositAmount must not be null");
        categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
        rates = rates == null ? List.of() : List.copyOf(rates);
    }
}
