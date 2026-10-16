package com.rentatools.billing.application.port.in;

import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.shared.domain.Actor;

import java.util.UUID;

/**
 * Consulta de lectura de una factura por su ID (verificando autorización de partícipe o admin).
 */
public interface GetInvoiceQuery {

    InvoiceView getInvoice(Actor actor, UUID invoiceId);
}
