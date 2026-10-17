package com.rentatools.rental.infrastructure.adapter.out.persistence.repository;

import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.ReservationJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para {@link ReservationJpaEntity}.
 */
public interface SpringDataReservationRepository extends JpaRepository<ReservationJpaEntity, UUID> {

    Optional<ReservationJpaEntity> findByCode(String code);

    boolean existsByCode(String code);

    Page<ReservationJpaEntity> findByClientIdOrderByCreatedAtDesc(UUID clientId, Pageable pageable);

    Page<ReservationJpaEntity> findByClientIdAndStatusOrderByCreatedAtDesc(UUID clientId, String status, Pageable pageable);

    Page<ReservationJpaEntity> findByProviderIdOrderByCreatedAtDesc(UUID providerId, Pageable pageable);

    Page<ReservationJpaEntity> findByProviderIdAndStatusOrderByCreatedAtDesc(UUID providerId, String status, Pageable pageable);

    Page<ReservationJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ReservationJpaEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
