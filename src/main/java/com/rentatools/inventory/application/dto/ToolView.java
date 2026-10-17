package com.rentatools.inventory.application.dto;

import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.inventory.domain.model.tool.RatePeriod;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolStatus;
import com.rentatools.shared.domain.Money;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Vista detallada de una herramienta del catálogo con sus categorías, tarifas, imágenes y disponibilidad.
 */
public record ToolView(
        UUID id,
        UUID providerId,
        String name,
        String brand,
        String model,
        String description,
        Money depositAmount,
        ToolStatus status,
        Set<UUID> categoryIds,
        Map<RatePeriod, Money> rates,
        List<ToolImageView> images,
        long availableUnitsCount,
        Instant createdAt,
        Instant updatedAt
) {
    public ToolView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(depositAmount, "depositAmount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
        rates = rates == null ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(rates));
        images = images == null ? List.of() : List.copyOf(images);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static ToolView from(Tool tool, long availableUnitsCount) {
        Set<UUID> categories = new LinkedHashSet<>();
        for (CategoryId categoryId : tool.categoryIds()) {
            categories.add(categoryId.value());
        }
        Map<RatePeriod, Money> ratePrices = new EnumMap<>(RatePeriod.class);
        tool.rates().forEach((period, rate) -> ratePrices.put(period, rate.price()));

        List<ToolImageView> imageViews = tool.images().stream()
                .map(ToolImageView::from)
                .toList();

        return new ToolView(
                tool.id().value(),
                tool.providerId().value(),
                tool.name(),
                tool.brand(),
                tool.model(),
                tool.description(),
                tool.depositAmount(),
                tool.status(),
                categories,
                ratePrices,
                imageViews,
                availableUnitsCount,
                tool.createdAt(),
                tool.updatedAt()
        );
    }
}
