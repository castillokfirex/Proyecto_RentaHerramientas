package com.rentatools.inventory.infrastructure.adapter.out.persistence.repository;

import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para {@link ToolJpaEntity}.
 */
public interface SpringDataToolRepository extends JpaRepository<ToolJpaEntity, UUID> {

    Page<ToolJpaEntity> findByProviderIdOrderByCreatedAtDesc(UUID providerId, Pageable pageable);

    Optional<ToolJpaEntity> findByProviderIdAndNameIgnoreCase(UUID providerId, String name);

    /**
     * Desplaza temporalmente las posiciones de las imágenes de una herramienta ({@code position + 1000})
     * para evitar colisiones con el índice único {@code uq_tool_images_tool_position (tool_id, position)}
     * durante el reordenamiento en la misma transacción (ver {@code DDL-4FN.md} §8 nota).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = "UPDATE tool_images SET position = position + 1000 WHERE tool_id = :toolId", nativeQuery = true)
    void shiftImagePositionsTemporarily(@Param("toolId") UUID toolId);
}
