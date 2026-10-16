package com.rentatools.billing.infrastructure.adapter.out.persistence.repository;

import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.DepositRetentionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para {@link DepositRetentionJpaEntity}.
 */
public interface SpringDataDepositRetentionRepository extends JpaRepository<DepositRetentionJpaEntity, UUID> {

    @Query("""
            SELECT r FROM DepositRetentionJpaEntity r
            WHERE r.invoiceId IN (
                SELECT i.id FROM InvoiceJpaEntity i WHERE i.reservationId = :reservationId
            )
            ORDER BY r.appliedAt ASC
            """)
    List<DepositRetentionJpaEntity> findByReservationId(@Param("reservationId") UUID reservationId);
}
