package com.rentatools.inventory.infrastructure.adapter.out.persistence;

import com.rentatools.inventory.api.UnitRef;
import com.rentatools.inventory.application.port.out.ToolUnitRepository;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.unit.SerialNumber;
import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolUnitJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.mapper.InventoryPersistenceMapper;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.repository.SpringDataToolUnitRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia JPA para el agregado {@link ToolUnit}, incluyendo el bloqueo
 * pesimista ({@code SELECT ... FOR UPDATE}) de unidades operativas.
 */
@Repository
@Transactional
public class ToolUnitPersistenceAdapter implements ToolUnitRepository {

    private final SpringDataToolUnitRepository repository;
    private final InventoryPersistenceMapper mapper;

    public ToolUnitPersistenceAdapter(
            SpringDataToolUnitRepository repository,
            InventoryPersistenceMapper mapper
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public ToolUnit save(ToolUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        ToolUnitJpaEntity entity = repository.findById(unit.id().value())
                .orElseGet(ToolUnitJpaEntity::new);
        mapper.updateEntity(unit, entity);
        ToolUnitJpaEntity saved = repository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ToolUnit> findById(ToolUnitId id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToolUnit> findByToolId(ToolId toolId) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        return repository.findByToolIdOrderBySerialNumberAsc(toolId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByToolIdAndSerialNumber(ToolId toolId, SerialNumber serialNumber) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(serialNumber, "serialNumber must not be null");
        return repository.existsByToolIdAndSerialNumberIgnoreCase(toolId.value(), serialNumber.value());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByToolIdAndStatus(ToolId toolId, UnitStatus status) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return repository.countByToolIdAndStatus(toolId.value(), status);
    }

    /**
     * Bloquea con {@code PESSIMISTIC_WRITE} ({@code SELECT ... FOR UPDATE}) todas las unidades
     * operativas ({@link UnitStatus#AVAILABLE}) asociadas a la herramienta indicada.
     */
    @Transactional
    public List<UnitRef> lockOperationalUnits(UUID toolId) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        return repository.lockByToolIdAndStatus(toolId, UnitStatus.AVAILABLE).stream()
                .map(u -> new UnitRef(u.getId(), u.getToolId(), u.getSerialNumber()))
                .toList();
    }
}
