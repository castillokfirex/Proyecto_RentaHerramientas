package com.rentatools.rental.domain.service;

import com.rentatools.rental.domain.exception.InsufficientAvailabilityException;
import com.rentatools.rental.domain.model.reservation.UnitCommitment;
import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DateRange;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de dominio encargado de filtrar las unidades operativas disponibles descartando aquellas
 * ocupadas por solapamiento de fechas o con devolución demorada, y asignar las primeras {@code quantity}
 * unidades disponibles.
 */
public class UnitAllocator {

    public UnitAllocator() {
    }

    /**
     * Asigna las primeras {@code requestedQuantity} unidades operativas que no estén en el conjunto
     * de unidades ocupadas ({@code occupiedUnits}).
     *
     * @throws InsufficientAvailabilityException si no hay suficientes unidades disponibles
     */
    public List<UnitId> allocate(
            UUID toolId,
            List<UnitId> operationalUnits,
            Set<UnitId> occupiedUnits,
            int requestedQuantity
    ) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(operationalUnits, "operationalUnits must not be null");
        Objects.requireNonNull(occupiedUnits, "occupiedUnits must not be null");
        if (requestedQuantity <= 0) {
            throw new BusinessRuleViolation("INVALID_REQUESTED_QUANTITY", "requestedQuantity must be >= 1");
        }

        List<UnitId> available = new ArrayList<>();
        Set<UnitId> seen = new HashSet<>();
        for (UnitId unitId : operationalUnits) {
            Objects.requireNonNull(unitId, "unitId must not be null");
            if (seen.add(unitId) && !occupiedUnits.contains(unitId)) {
                available.add(unitId);
            }
        }

        if (available.size() < requestedQuantity) {
            throw new InsufficientAvailabilityException(toolId, requestedQuantity, available.size());
        }

        return List.copyOf(available.subList(0, requestedQuantity));
    }

    /**
     * Evalúa los compromisos existentes ({@link UnitCommitment}) frente al rango solicitado ({@code requestedRange}),
     * descarta las unidades ocupadas por cruce de fechas o devolución demorada, y asigna las primeras
     * {@code requestedQuantity} unidades disponibles.
     *
     * @throws InsufficientAvailabilityException si no hay suficientes unidades disponibles
     */
    public List<UnitId> allocate(
            UUID toolId,
            List<UnitId> operationalUnits,
            Collection<UnitCommitment> commitments,
            DateRange requestedRange,
            int requestedQuantity
    ) {
        Set<UnitId> occupied = findOccupiedUnits(commitments, requestedRange);
        return allocate(toolId, operationalUnits, occupied, requestedQuantity);
    }

    /**
     * Determina el conjunto de unidades ocupadas para un rango de fechas dado a partir de los compromisos existentes.
     */
    public Set<UnitId> findOccupiedUnits(Collection<UnitCommitment> commitments, DateRange requestedRange) {
        Objects.requireNonNull(commitments, "commitments must not be null");
        Objects.requireNonNull(requestedRange, "requestedRange must not be null");

        Set<UnitId> occupied = new LinkedHashSet<>();
        for (UnitCommitment commitment : commitments) {
            Objects.requireNonNull(commitment, "commitment must not be null");
            if (commitment.blocks(requestedRange)) {
                occupied.add(commitment.unitId());
            }
        }
        return Set.copyOf(occupied);
    }
}
