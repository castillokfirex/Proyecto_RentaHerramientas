package com.rentatools.inventory.application.dto;

import com.rentatools.shared.domain.Money;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Comando para actualizar los datos descriptivos, depósito y categorías de una herramienta.
 */
public record UpdateToolCommand(
        UUID toolId,
        String name,
        String brand,
        String model,
        String description,
        Money depositAmount,
        Set<UUID> categoryIds
) {
    public UpdateToolCommand {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(depositAmount, "depositAmount must not be null");
        categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
    }
}
