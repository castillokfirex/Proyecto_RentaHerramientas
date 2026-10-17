package com.rentatools.billing.infrastructure.adapter.out.persistence;

import com.rentatools.billing.application.port.out.InvoiceRepository;
import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceId;
import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.InvoiceJpaEntity;
import com.rentatools.billing.infrastructure.adapter.out.persistence.mapper.InvoicePersistenceMapper;
import com.rentatools.billing.infrastructure.adapter.out.persistence.repository.SpringDataInvoiceRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de salida de persistencia para {@link InvoiceRepository}.
 */
@Repository
public class InvoicePersistenceAdapter implements InvoiceRepository {

    private final SpringDataInvoiceRepository springDataInvoiceRepository;
    private final InvoicePersistenceMapper mapper;

    public InvoicePersistenceAdapter(
            SpringDataInvoiceRepository springDataInvoiceRepository,
            InvoicePersistenceMapper mapper
    ) {
        this.springDataInvoiceRepository = springDataInvoiceRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Invoice save(Invoice invoice) {
        InvoiceJpaEntity entity = springDataInvoiceRepository.findById(invoice.id().value())
                .map(existing -> mapper.updateEntity(existing, invoice))
                .orElseGet(() -> mapper.toNewEntity(invoice));
        InvoiceJpaEntity saved = springDataInvoiceRepository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findById(InvoiceId id) {
        return springDataInvoiceRepository.findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findByReservationIdAndType(UUID reservationId, InvoiceType type) {
        return springDataInvoiceRepository.findByReservationIdAndType(reservationId, type)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findByReservationId(UUID reservationId) {
        return springDataInvoiceRepository.findByReservationId(reservationId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findAll() {
        return springDataInvoiceRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(InvoiceId id) {
        springDataInvoiceRepository.deleteById(id.value());
        springDataInvoiceRepository.flush();
    }
}
