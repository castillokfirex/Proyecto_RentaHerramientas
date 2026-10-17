package com.rentatools.inventory.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato publico de gestion de unidades fisicas del contexto {@code inventory}.
 *
 * <p>Expone operaciones para el control de inventario, asignacion concurrente mediante
 * bloqueo pesimista y gestion del ciclo de vida operativo de las unidades fisicas.</p>
 */
public interface UnitInventoryApi {

    /**
     * Bloquea a nivel de base de datos (mediante {@code SELECT ... FOR UPDATE}) las unidades
     * fisicas operacionales (estado {@code AVAILABLE}) asociadas a la herramienta indicada.
     *
     * <p><strong>Restriccion transaccional:</strong> Debe invocarse obligatoriamente dentro de
     * una transaccion activa en el contexto consumidor (por ejemplo, al crear una reserva en
     * {@code rental}) para serializar reservas concurrentes sobre la misma herramienta y evitar
     * sobreasignacion (overbooking).</p>
     *
     * @param toolId Identificador unico de la herramienta cuyas unidades operativas se desean bloquear
     * @return Lista inmutable de referencias a las unidades fisicas bloqueadas
     */
    List<UnitRef> lockOperationalUnits(UUID toolId);

    /**
     * Busca la referencia inmutable de una unidad fisica por su identificador unico.
     *
     * @param unitId Identificador unico de la unidad fisica
     * @return {@link Optional} con la referencia de la unidad si existe, o vacio en caso contrario
     */
    Optional<UnitRef> findUnit(UUID unitId);

    /**
     * Registra el envio de una unidad fisica al estado {@code MAINTENANCE} preventivo o correctivo
     * (por ejemplo, tras identificarse averias o danos en la inspeccion de devolucion).
     *
     * @param unitId Identificador unico de la unidad fisica
     * @param reason Motivo o justificacion del ingreso a mantenimiento
     */
    void sendToMaintenance(UUID unitId, String reason);

    /**
     * Registra la baja o retiro definitivo (estado {@code RETIRED}) de una unidad fisica del inventario
     * (por ejemplo, en caso de perdida total, robo o destruccion).
     *
     * @param unitId Identificador unico de la unidad fisica
     * @param reason Motivo o justificacion de la baja definitiva
     */
    void retire(UUID unitId, String reason);
}
