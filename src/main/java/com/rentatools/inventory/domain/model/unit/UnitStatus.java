package com.rentatools.inventory.domain.model.unit;

/**
 * Estado operativo de una unidad física de herramienta.
 *
 * <p>Por normalización 3FN (ver {@code DDL-4FN.md}), no existe el estado {@code RENTED}:
 * que una unidad esté alquilada en un rango de fechas se deriva de {@code reservation_units}.
 *
 * <p>Transiciones permitidas: {@code AVAILABLE <-> MAINTENANCE -> RETIRED} (terminal).
 */
public enum UnitStatus {
    AVAILABLE,
    MAINTENANCE,
    RETIRED
}
