package com.rentatools.inventory.infrastructure.adapter.out.persistence.repository;

import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.CategoryJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para {@link CategoryJpaEntity}.
 */
public interface SpringDataCategoryRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    List<CategoryJpaEntity> findAllByOrderByNameAsc();

    Optional<CategoryJpaEntity> findByParentIdIsNullAndNameIgnoreCase(String name);

    Optional<CategoryJpaEntity> findByParentIdAndNameIgnoreCase(UUID parentId, String name);
}
