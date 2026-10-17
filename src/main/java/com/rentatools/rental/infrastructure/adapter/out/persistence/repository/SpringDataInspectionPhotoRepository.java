package com.rentatools.rental.infrastructure.adapter.out.persistence.repository;

import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.InspectionPhotoJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para {@link InspectionPhotoJpaEntity}.
 */
public interface SpringDataInspectionPhotoRepository extends JpaRepository<InspectionPhotoJpaEntity, UUID> {

    Optional<InspectionPhotoJpaEntity> findByStorageKey(String storageKey);
}
