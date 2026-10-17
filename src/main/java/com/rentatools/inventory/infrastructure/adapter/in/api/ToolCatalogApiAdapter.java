package com.rentatools.inventory.infrastructure.adapter.in.api;

import com.rentatools.inventory.api.RatePeriod;
import com.rentatools.inventory.api.ToolCatalogApi;
import com.rentatools.inventory.api.ToolRentalInfo;
import com.rentatools.inventory.application.port.out.ToolRepository;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.tool.ToolStatus;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de API interna que implementa {@link ToolCatalogApi} para exponer información
 * comercial y tarifaria de herramientas al contexto {@code rental}.
 */
@Component
@Transactional(readOnly = true)
public class ToolCatalogApiAdapter implements ToolCatalogApi {

    private final ToolRepository toolRepository;

    public ToolCatalogApiAdapter(ToolRepository toolRepository) {
        this.toolRepository = Objects.requireNonNull(toolRepository, "toolRepository must not be null");
    }

    @Override
    public Optional<ToolRentalInfo> findRentableTool(UUID toolId) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        return toolRepository.findById(ToolId.of(toolId)).map(this::toRentalInfo);
    }

    private ToolRentalInfo toRentalInfo(Tool tool) {
        Map<RatePeriod, BigDecimal> apiRates = new EnumMap<>(RatePeriod.class);
        tool.rates().forEach((domainPeriod, rate) -> {
            RatePeriod apiPeriod = RatePeriod.valueOf(domainPeriod.name());
            apiRates.put(apiPeriod, rate.price().amount());
        });

        return new ToolRentalInfo(
                tool.id().value(),
                tool.providerId().value(),
                tool.name(),
                tool.status() == ToolStatus.ACTIVE,
                tool.depositAmount().amount(),
                apiRates
        );
    }
}
