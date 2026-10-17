package com.rentatools.rental.infrastructure.adapter.out.inventory;

import com.rentatools.inventory.api.RatePeriod;
import com.rentatools.inventory.api.ToolCatalogApi;
import com.rentatools.inventory.api.ToolRentalInfo;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.domain.model.pricing.RateCard;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptador anticorrupción (ACL) que implementa {@link ToolCatalogPort} consumiendo {@link ToolCatalogApi}
 * de {@code inventory.api}.
 */
@Component
public class InventoryToolCatalogAdapter implements ToolCatalogPort {

    private final ToolCatalogApi toolCatalogApi;

    public InventoryToolCatalogAdapter(ToolCatalogApi toolCatalogApi) {
        this.toolCatalogApi = Objects.requireNonNull(toolCatalogApi, "toolCatalogApi must not be null");
    }

    @Override
    public Optional<RentableToolDescriptor> findRentableTool(UUID toolId) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        return toolCatalogApi.findRentableTool(toolId)
                .map(this::toDescriptor);
    }

    private RentableToolDescriptor toDescriptor(ToolRentalInfo info) {
        Map<RatePeriod, BigDecimal> rates = info.rates();
        BigDecimal dayAmount = rates.get(RatePeriod.DAY);
        if (dayAmount == null) {
            throw new BusinessRuleViolation("MISSING_DAILY_RATE", "Tool " + info.toolId() + " has no DAY rate configured");
        }
        Money dailyRate = Money.of(dayAmount);
        Money weeklyRate = rates.containsKey(RatePeriod.WEEK) ? Money.of(rates.get(RatePeriod.WEEK)) : null;
        Money monthlyRate = rates.containsKey(RatePeriod.MONTH) ? Money.of(rates.get(RatePeriod.MONTH)) : null;
        Money depositAmount = Money.of(info.depositAmount());

        RateCard rateCard = RateCard.of(dailyRate, weeklyRate, monthlyRate, depositAmount);
        return new RentableToolDescriptor(
                info.toolId(),
                info.providerId(),
                info.name(),
                info.active(),
                rateCard
        );
    }
}
