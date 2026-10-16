package com.rentatools.billing.infrastructure.adapter.out.persistence.repository;

import com.rentatools.billing.domain.model.invoice.InvoiceType;
import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.InvoiceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para {@link InvoiceJpaEntity}.
 */
public interface SpringDataInvoiceRepository extends JpaRepository<InvoiceJpaEntity, UUID> {

    Optional<InvoiceJpaEntity> findByReservationIdAndType(UUID reservationId, InvoiceType type);

    List<InvoiceJpaEntity> findByReservationId(UUID reservationId);

    @Query("SELECT COALESCE(MAX(i.number), 0) FROM InvoiceJpaEntity i")
    long findMaxInvoiceNumber();
}
