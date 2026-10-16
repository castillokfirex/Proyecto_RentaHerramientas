package com.rentatools.billing.application.port.out;

import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceNumber;

import java.time.Instant;
import java.util.Objects;

/**
 * Puerto de salida para asignar números secuenciales de factura ({@code MAX(number) + 1}).
 */
public interface InvoiceNumberAllocator {

    /**
     * Obtiene el siguiente número de factura secuencial disponible.
     */
    InvoiceNumber allocateNext();

    /**
     * Emite una factura en borrador asignándole el siguiente número secuencial disponible y la persiste,
     * reintentando automáticamente si se produce una colisión concurrente de numeración.
     */
    default Invoice issueAndSave(Invoice draftInvoice, Instant issuedAt, InvoiceRepository invoiceRepository) {
        Objects.requireNonNull(draftInvoice, "draftInvoice must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");

        InvoiceNumber next = allocateNext();
        draftInvoice.issue(next, issuedAt);
        return invoiceRepository.save(draftInvoice);
    }
}
