package com.rentatools.inventory.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato publico expuesto por el contexto {@code inventory} para consultar
 * el catalogo de herramientas y sus condiciones de alquiler.
 *
 * <p>Consumido por el contexto {@code rental} para cotizaciones y validacion
 * de herramientas previas a la creacion de reservas.</p>
 */
public interface ToolCatalogApi {

    /**
     * Consulta los datos comerciales y de tarifas de una herramienta para fines de alquiler.
     *
     * @param toolId Identificador unico de la herramienta
     * @return {@link Optional} con la informacion comercial si la herramienta existe, o vacio en caso contrario
     */
    Optional<ToolRentalInfo> findRentableTool(UUID toolId);
}
