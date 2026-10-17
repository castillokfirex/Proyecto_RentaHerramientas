package com.rentatools.rental.application.port.out;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida (ACL) hacia el inventario de unidades físicas para el bloqueo pesimista
 * ({@code SELECT ... FOR UPDATE}) y consulta/actualización de unidades.
 */
public interface UnitInventoryPort {

    /**
     * Bloquea con {@code SELECT ... FOR UPDATE} las unidades operativas ({@code AVAILABLE}) de una herramienta.
     */
    List<OperationalUnitDescriptor> lockOperationalUnits(UUID toolId);

    Optional<OperationalUnitDescriptor> findUnit(UUID unitId);

    void sendToMaintenance(UUID unitId, String reason);

    void retire(UUID unitId, String reason);

    record OperationalUnitDescriptor(
            UUID unitId,
            UUID toolId,
            String serialNumber
    ) {
        public OperationalUnitDescriptor {
            Objects.requireNonNull(unitId, "unitId must not be null");
            Objects.requireNonNull(toolId, "toolId must not be null");
            Objects.requireNonNull(serialNumber, "serialNumber must not be null");
        }
    }
}
