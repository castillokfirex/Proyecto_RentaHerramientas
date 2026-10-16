package com.rentatools.billing.application.port.out;

import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia del agregado {@link Invoice}.
 */
public interface InvoiceRepository {

    Invoice save(Invoice invoice);

    Optional<Invoice> findById(InvoiceId id);

    Optional<Invoice> findByReservationIdAndType(UUID reservationId, InvoiceType type);

    List<Invoice> findByReservationId(UUID reservationId);

    void delete(InvoiceId id);
}
