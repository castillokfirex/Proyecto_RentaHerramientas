package com.rentatools.rental.application.mapper;

import com.rentatools.rental.application.dto.ChecklistItemView;
import com.rentatools.rental.domain.model.checklist.ChecklistItem;
import java.util.Objects;

/**
 * Mapper para convertir agregados {@link ChecklistItem} en {@link ChecklistItemView}.
 */
public final class ChecklistViewMapper {

    private ChecklistViewMapper() {
    }

    public static ChecklistItemView toView(ChecklistItem item) {
        Objects.requireNonNull(item, "item must not be null");
        return new ChecklistItemView(
                item.id() != null ? item.id().value() : 0,
                item.code(),
                item.label(),
                item.isActive()
        );
    }
}
