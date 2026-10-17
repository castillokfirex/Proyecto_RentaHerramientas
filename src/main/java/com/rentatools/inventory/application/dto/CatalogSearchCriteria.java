package com.rentatools.inventory.application.dto;

import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Money;
import java.util.UUID;

/**
 * Criterios de búsqueda y filtrado del catálogo público de herramientas.
 */
public record CatalogSearchCriteria(
        String query,
        UUID categoryId,
        Money minDayPrice,
        Money maxDayPrice,
        DateRange availableRange
) {
    public CatalogSearchCriteria {
        if (query != null) {
            String trimmed = query.trim();
            query = trimmed.isEmpty() ? null : trimmed;
        }
        if (minDayPrice != null && minDayPrice.isNegative()) {
            throw new IllegalArgumentException("minDayPrice must be >= 0");
        }
        if (maxDayPrice != null && maxDayPrice.isNegative()) {
            throw new IllegalArgumentException("maxDayPrice must be >= 0");
        }
        if (minDayPrice != null && maxDayPrice != null && minDayPrice.compareTo(maxDayPrice) > 0) {
            throw new IllegalArgumentException("minDayPrice must be <= maxDayPrice");
        }
    }
}
