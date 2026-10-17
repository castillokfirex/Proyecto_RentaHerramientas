package com.rentatools.rental.application.port.out;

import com.rentatools.rental.domain.model.pricing.RateCard;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida (ACL) hacia el catálogo de herramientas para obtener tarifas y estado de publicación.
 */
public interface ToolCatalogPort {

    Optional<RentableToolDescriptor> findRentableTool(UUID toolId);

    record RentableToolDescriptor(
            UUID toolId,
            UUID providerId,
            String name,
            boolean active,
            RateCard rateCard
    ) {
        public RentableToolDescriptor {
            Objects.requireNonNull(toolId, "toolId must not be null");
            Objects.requireNonNull(providerId, "providerId must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(rateCard, "rateCard must not be null");
        }
    }
}
