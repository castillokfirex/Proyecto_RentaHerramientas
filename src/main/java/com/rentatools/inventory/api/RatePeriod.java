package com.rentatools.inventory.api;

/**
 * Periodos de tarificacion admitidos para el calculo de costos de alquiler de herramientas.
 *
 * <p>El modelo comercial de RentaTools permite definir tarifas por dia, por semana
 * y por mes para cada herramienta registrada en el catalogo.</p>
 */
public enum RatePeriod {
    /** Tarifa base por dia de alquiler. */
    DAY,

    /** Tarifa aplicable a periodos semanales (7 dias consecutivos). */
    WEEK,

    /** Tarifa aplicable a periodos mensuales (30 dias consecutivos). */
    MONTH
}
