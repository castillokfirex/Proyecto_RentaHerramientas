package com.rentatools.rental.application.port.out;

import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.shared.domain.DateRange;
import java.util.Collection;
import java.util.Set;

/**
 * Puerto de salida para consultar qué unidades físicas candidatas se encuentran ocupadas
 * para un rango de fechas dado (por solapamiento de reservas o por devolución demorada).
 */
public interface UnitAvailabilityPort {

    Set<UnitId> findOccupiedUnits(Collection<UnitId> candidateUnitIds, DateRange dateRange);
}
