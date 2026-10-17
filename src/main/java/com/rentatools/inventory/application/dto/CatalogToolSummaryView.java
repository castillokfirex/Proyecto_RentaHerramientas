package com.rentatools.inventory.application.dto;

import com.rentatools.shared.domain.Money;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Vista resumida de una herramienta activa para listados y búsquedas del catálogo público.
 */
public record CatalogToolSummaryView(
        UUID id,
        UUID providerId,
        String name,
        String brand,
        String model,
        Money depositAmount,
        Money dayRate,
        String coverImageUrl,
        Set<UUID> categoryIds,
        long availableUnitsCount
) {
    public CatalogToolSummaryView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(depositAmount, "depositAmount must not be null");
        Objects.requireNonNull(dayRate, "dayRate must not be null");
        categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
    }
}
