package com.rentatools.rental.infrastructure.adapter.out.persistence.repository;

import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.InspectionJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para {@link InspectionJpaEntity}.
 */
public interface SpringDataInspectionRepository extends JpaRepository<InspectionJpaEntity, UUID> {

    List<InspectionJpaEntity> findByReservationUnitIdOrderByPerformedAtAsc(UUID reservationUnitId);

    Optional<InspectionJpaEntity> findByReservationUnitIdAndType(UUID reservationUnitId, String type);

    @Query("SELECT i FROM InspectionJpaEntity i WHERE i.reservationUnitId IN "
            + "(SELECT u.id FROM ReservationUnitJpaEntity u WHERE u.reservation.id = :reservationId) "
            + "ORDER BY i.performedAt ASC")
    List<InspectionJpaEntity> findByReservationId(@Param("reservationId") UUID reservationId);
}
