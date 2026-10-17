package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import com.rentatools.inventory.application.dto.CatalogToolSummaryView;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * DTO de respuesta resumida para listados y búsqueda del catálogo público.
 */
public record CatalogToolSummaryResponse(
        UUID id,
        UUID providerId,
        String name,
        String brand,
        String model,
        BigDecimal depositAmount,
        BigDecimal dayRate,
        String coverImageUrl,
        Set<UUID> categoryIds,
        long availableUnitsCount
) {
    public static CatalogToolSummaryResponse from(CatalogToolSummaryView view) {
        return new CatalogToolSummaryResponse(
                view.id(),
                view.providerId(),
                view.name(),
                view.brand(),
                view.model(),
                view.depositAmount().amount(),
                view.dayRate().amount(),
                view.coverImageUrl(),
                view.categoryIds(),
                view.availableUnitsCount()
        );
    }
}
