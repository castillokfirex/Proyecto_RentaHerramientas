package com.rentatools.rental.infrastructure.adapter.out.persistence.repository;

import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.ChecklistItemJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para {@link ChecklistItemJpaEntity}.
 */
public interface SpringDataChecklistItemRepository extends JpaRepository<ChecklistItemJpaEntity, Integer> {

    Optional<ChecklistItemJpaEntity> findByCode(String code);

    List<ChecklistItemJpaEntity> findByActiveTrue();
}
