package com.rentatools.inventory.infrastructure.adapter.in.web.dto;

import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.inventory.domain.model.tool.RatePeriod;
import com.rentatools.inventory.domain.model.tool.ToolStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DTO de respuesta detallada para una herramienta del catálogo.
 */
public record ToolResponse(
        UUID id,
        UUID providerId,
        String name,
        String brand,
        String model,
        String description,
        BigDecimal depositAmount,
        ToolStatus status,
        Set<UUID> categoryIds,
        Map<RatePeriod, BigDecimal> rates,
        List<ToolImageResponse> images,
        long availableUnitsCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ToolResponse from(ToolView view) {
        Map<RatePeriod, BigDecimal> rateMap = new EnumMap<>(RatePeriod.class);
        view.rates().forEach((period, money) -> rateMap.put(period, money.amount()));

        List<ToolImageResponse> imageResponses = view.images().stream()
                .map(ToolImageResponse::from)
                .toList();

        return new ToolResponse(
                view.id(),
                view.providerId(),
                view.name(),
                view.brand(),
                view.model(),
                view.description(),
                view.depositAmount().amount(),
                view.status(),
                view.categoryIds(),
                rateMap,
                imageResponses,
                view.availableUnitsCount(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
