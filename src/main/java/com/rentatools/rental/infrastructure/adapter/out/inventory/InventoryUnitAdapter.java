package com.rentatools.rental.infrastructure.adapter.out.inventory;

import com.rentatools.inventory.api.UnitInventoryApi;
import com.rentatools.inventory.api.UnitRef;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptador anticorrupción (ACL) que implementa {@link UnitInventoryPort} consumiendo {@link UnitInventoryApi}
 * de {@code inventory.api}.
 */
@Component
public class InventoryUnitAdapter implements UnitInventoryPort {

    private final UnitInventoryApi unitInventoryApi;

    public InventoryUnitAdapter(UnitInventoryApi unitInventoryApi) {
        this.unitInventoryApi = Objects.requireNonNull(unitInventoryApi, "unitInventoryApi must not be null");
    }

    @Override
    public List<OperationalUnitDescriptor> lockOperationalUnits(UUID toolId) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        return unitInventoryApi.lockOperationalUnits(toolId).stream()
                .map(this::toDescriptor)
                .toList();
    }

    @Override
    public Optional<OperationalUnitDescriptor> findUnit(UUID unitId) {
        Objects.requireNonNull(unitId, "unitId must not be null");
        return unitInventoryApi.findUnit(unitId)
                .map(this::toDescriptor);
    }

    @Override
    public void sendToMaintenance(UUID unitId, String reason) {
        Objects.requireNonNull(unitId, "unitId must not be null");
        unitInventoryApi.sendToMaintenance(unitId, reason);
    }

    @Override
    public void retire(UUID unitId, String reason) {
        Objects.requireNonNull(unitId, "unitId must not be null");
        unitInventoryApi.retire(unitId, reason);
    }

    private OperationalUnitDescriptor toDescriptor(UnitRef ref) {
        return new OperationalUnitDescriptor(
                ref.unitId(),
                ref.toolId(),
                ref.serialNumber()
        );
    }
}
