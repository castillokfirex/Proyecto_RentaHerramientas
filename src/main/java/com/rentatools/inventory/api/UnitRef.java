package com.rentatools.inventory.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Referencia inmutable a una unidad fisica especifica perteneciente al inventario de una herramienta.
 *
 * @param unitId Identificador unico de la unidad fisica (UUID)
 * @param toolId Identificador unico de la herramienta del catalogo a la que pertenece la unidad
 * @param serialNumber Numero de serie fisico unico asignado a la unidad
 */
public record UnitRef(
        UUID unitId,
        UUID toolId,
        String serialNumber
) {

    public UnitRef {
        Objects.requireNonNull(unitId, "unitId must not be null");
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(serialNumber, "serialNumber must not be null");
    }
}
