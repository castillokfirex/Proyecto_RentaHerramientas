package com.rentatools.billing.infrastructure.adapter.out.persistence.repository;

import com.rentatools.billing.infrastructure.adapter.out.persistence.entity.SecurityDepositJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para {@link SecurityDepositJpaEntity}.
 */
public interface SpringDataSecurityDepositRepository extends JpaRepository<SecurityDepositJpaEntity, UUID> {

    Optional<SecurityDepositJpaEntity> findByReservationId(UUID reservationId);
}
