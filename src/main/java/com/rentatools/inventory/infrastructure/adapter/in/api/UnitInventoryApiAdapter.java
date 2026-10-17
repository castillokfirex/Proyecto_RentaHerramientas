package com.rentatools.inventory.infrastructure.adapter.in.api;

import com.rentatools.inventory.api.UnitInventoryApi;
import com.rentatools.inventory.api.UnitRef;
import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.ToolUnitPersistenceAdapter;
import com.rentatools.shared.application.exception.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de API interna que implementa {@link UnitInventoryApi} delegando en
 * {@link ToolUnitPersistenceAdapter} para el bloqueo pesimista ({@code SELECT ... FOR UPDATE})
 * y la gestión del estado operativo de unidades físicas.
 */
@Component
@Transactional
public class UnitInventoryApiAdapter implements UnitInventoryApi {

    private final ToolUnitPersistenceAdapter unitPersistenceAdapter;
    private final Clock clock;

    public UnitInventoryApiAdapter(ToolUnitPersistenceAdapter unitPersistenceAdapter, Clock clock) {
        this.unitPersistenceAdapter = Objects.requireNonNull(
                unitPersistenceAdapter,
                "unitPersistenceAdapter must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public List<UnitRef> lockOperationalUnits(UUID toolId) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        return unitPersistenceAdapter.lockOperationalUnits(toolId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UnitRef> findUnit(UUID unitId) {
        Objects.requireNonNull(unitId, "unitId must not be null");
        return unitPersistenceAdapter.findById(ToolUnitId.of(unitId))
                .map(u -> new UnitRef(u.id().value(), u.toolId().value(), u.serialNumber().value()));
    }

    @Override
    public void sendToMaintenance(UUID unitId, String reason) {
        Objects.requireNonNull(unitId, "unitId must not be null");
        ToolUnit unit = loadUnitOrThrow(unitId);
        unit.changeStatus(UnitStatus.MAINTENANCE, reason, clock.instant());
        unitPersistenceAdapter.save(unit);
    }

    @Override
    public void retire(UUID unitId, String reason) {
        Objects.requireNonNull(unitId, "unitId must not be null");
        ToolUnit unit = loadUnitOrThrow(unitId);
        unit.changeStatus(UnitStatus.RETIRED, reason, clock.instant());
        unitPersistenceAdapter.save(unit);
    }

    private ToolUnit loadUnitOrThrow(UUID unitId) {
        ToolUnitId id = ToolUnitId.of(unitId);
        return unitPersistenceAdapter.findById(id)
                .orElseThrow(() -> new NotFoundException("TOOL_UNIT_NOT_FOUND", "ToolUnit not found: " + id));
    }
}
