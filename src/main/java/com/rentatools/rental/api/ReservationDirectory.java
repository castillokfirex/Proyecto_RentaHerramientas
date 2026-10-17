package com.rentatools.rental.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto publico expuesto por el contexto {@code rental} para consultar
 * informacion basica de reservas y sus participantes.
 *
 * <p>Consumido por otros contextos acotados (como {@code billing}) para validar
 * permisos de acceso, emitir facturacion y asociar clientes y proveedores sin acoplar
 * el modelo interno de agregados de alquiler.</p>
 */
public interface ReservationDirectory {

    /**
     * Busca los datos identificatorios de los participantes (cliente y proveedor) de una reserva.
     *
     * @param reservationId Identificador unico de la reserva
     * @return {@link Optional} con los datos de los participantes si la reserva existe, o vacio en caso contrario
     */
    Optional<ReservationParticipants> findParticipants(UUID reservationId);
}
