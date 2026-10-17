package com.rentatools.inventory.api;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Informacion comercial y tarifaria de una herramienta expuesta por {@code inventory}
 * para consulta y cotizacion desde el contexto {@code rental}.
 *
 * @param toolId Identificador unico de la herramienta en el catalogo
 * @param providerId Identificador del usuario proveedor propietario de la herramienta
 * @param name Nombre comercial o denominacion de la herramienta
 * @param active Indica si la herramienta se encuentra activa y habilitada para reservas
 * @param depositAmount Monto requerido en concepto de deposito en garantia (Security Deposit)
 * @param rates Mapa inmutable de tarifas configuradas por periodo (DAY, WEEK, MONTH)
 */
public record ToolRentalInfo(
        UUID toolId,
        UUID providerId,
        String name,
        boolean active,
        BigDecimal depositAmount,
        Map<RatePeriod, BigDecimal> rates
) {

    public ToolRentalInfo {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(depositAmount, "depositAmount must not be null");
        Objects.requireNonNull(rates, "rates must not be null");
        rates = Collections.unmodifiableMap(new EnumMap<>(rates));
    }
}
