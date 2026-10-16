package com.rentatools.billing.application.port.in;

import com.rentatools.billing.application.dto.ReservationBillingView;
import com.rentatools.shared.domain.Actor;

import java.util.UUID;

/**
 * Consulta consolidada de facturación y garantía de una reserva (verificando autorización de partícipe o admin).
 */
public interface GetReservationBillingQuery {

    ReservationBillingView getReservationBilling(Actor actor, UUID reservationId);
}
