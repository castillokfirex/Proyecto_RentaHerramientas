package com.rentatools.billing.infrastructure.adapter.out.persistence;

import com.rentatools.billing.application.port.out.InvoiceNumberAllocator;
import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceNumber;
import com.rentatools.billing.infrastructure.adapter.out.persistence.repository.SpringDataInvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Asignador de números secuenciales de factura basado en {@code COALESCE(MAX(number), 0) + 1}
 * protegido por la restricción {@code uq_invoices_number} de {@code V4__billing.sql} y con
 * mecanismo de reintento ante colisión concurrente.
 */
@Component
public class MaxPlusOneInvoiceNumberAllocator implements InvoiceNumberAllocator {

    private static final int DEFAULT_MAX_RETRIES = 10;

    private final SpringDataInvoiceRepository springDataInvoiceRepository;
    private final ReentrantLock allocationLock = new ReentrantLock();
    private final int maxRetries;

    @Autowired
    public MaxPlusOneInvoiceNumberAllocator(SpringDataInvoiceRepository springDataInvoiceRepository) {
        this(springDataInvoiceRepository, DEFAULT_MAX_RETRIES);
    }

    MaxPlusOneInvoiceNumberAllocator(SpringDataInvoiceRepository springDataInvoiceRepository, int maxRetries) {
        this.springDataInvoiceRepository = Objects.requireNonNull(
                springDataInvoiceRepository,
                "springDataInvoiceRepository must not be null"
        );
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries must be positive");
        }
        this.maxRetries = maxRetries;
    }

    @Override
    public InvoiceNumber allocateNext() {
        long currentMax = springDataInvoiceRepository.findMaxInvoiceNumber();
        return InvoiceNumber.of(currentMax + 1L);
    }

    @Override
    public Invoice issueAndSave(Invoice draftInvoice, Instant issuedAt, InvoiceRepository invoiceRepository) {
        Objects.requireNonNull(draftInvoice, "draftInvoice must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(invoiceRepository, "invoiceRepository must not be null");

        DataIntegrityViolationException lastCollision = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            allocationLock.lock();
            try {
                InvoiceNumber candidate = allocateNext();
                if (draftInvoice.isDraft()) {
                    draftInvoice.issue(candidate, issuedAt);
                } else {
                    draftInvoice.reassignNumber(candidate);
                }
                return invoiceRepository.save(draftInvoice);
            } catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
                lastCollision = (ex instanceof DataIntegrityViolationException dive)
                        ? dive
                        : new DataIntegrityViolationException("Locking failure while allocating invoice number", ex);
            } finally {
                allocationLock.unlock();
            }
        }
        throw new IllegalStateException(
                "Could not allocate a unique invoice number after " + maxRetries + " attempts",
                lastCollision
        );
    }
}
