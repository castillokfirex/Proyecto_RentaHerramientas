package com.rentatools.rental.application.port.out;

import com.rentatools.rental.domain.model.checklist.ChecklistItem;
import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia del catálogo {@link ChecklistItem}.
 */
public interface ChecklistItemRepository {

    ChecklistItem save(ChecklistItem item);

    Optional<ChecklistItem> findById(ChecklistItemId id);

    Optional<ChecklistItem> findByCode(String code);

    List<ChecklistItem> findAll();

    List<ChecklistItem> findAllActive();
}
