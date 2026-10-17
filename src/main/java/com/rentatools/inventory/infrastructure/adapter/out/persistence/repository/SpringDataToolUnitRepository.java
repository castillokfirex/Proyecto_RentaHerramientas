package com.rentatools.inventory.infrastructure.adapter.out.persistence.repository;

import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolUnitJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para {@link ToolUnitJpaEntity}, incluyendo bloqueo pesimista
 * ({@code SELECT ... FOR UPDATE}) de unidades operativas.
 */
public interface SpringDataToolUnitRepository extends JpaRepository<ToolUnitJpaEntity, UUID> {

    List<ToolUnitJpaEntity> findByToolIdOrderBySerialNumberAsc(UUID toolId);

    boolean existsByToolIdAndSerialNumberIgnoreCase(UUID toolId, String serialNumber);

    long countByToolIdAndStatus(UUID toolId, UnitStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM ToolUnitJpaEntity u WHERE u.toolId = :toolId AND u.status = :status ORDER BY u.serialNumber ASC")
    List<ToolUnitJpaEntity> lockByToolIdAndStatus(
            @Param("toolId") UUID toolId,
            @Param("status") UnitStatus status
    );
}
