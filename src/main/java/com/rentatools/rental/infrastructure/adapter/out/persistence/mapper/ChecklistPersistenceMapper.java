package com.rentatools.rental.infrastructure.adapter.out.persistence.mapper;

import com.rentatools.rental.domain.model.checklist.ChecklistItem;
import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import com.rentatools.rental.infrastructure.adapter.out.persistence.entity.ChecklistItemJpaEntity;
import java.util.Objects;

/**
 * Mapper entre el agregado {@link ChecklistItem} y la entidad JPA {@link ChecklistItemJpaEntity}.
 */
public final class ChecklistPersistenceMapper {

    private ChecklistPersistenceMapper() {
    }

    public static ChecklistItem toDomain(ChecklistItemJpaEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        return ChecklistItem.reconstruct(
                ChecklistItemId.of(entity.getId() != null ? entity.getId().intValue() : 0),
                entity.getCode(),
                entity.getLabel(),
                entity.isActive()
        );
    }

    public static ChecklistItemJpaEntity toJpaEntity(ChecklistItem domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        ChecklistItemJpaEntity entity = new ChecklistItemJpaEntity();
        if (domain.id() != null) {
            entity.setId((short) domain.id().value());
        }
        entity.setCode(domain.code());
        entity.setLabel(domain.label());
        entity.setActive(domain.isActive());
        return entity;
    }

    public static void updateManagedEntity(ChecklistItemJpaEntity managed, ChecklistItem domain) {
        Objects.requireNonNull(managed, "managed must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        managed.setCode(domain.code());
        managed.setLabel(domain.label());
        managed.setActive(domain.isActive());
    }
}
