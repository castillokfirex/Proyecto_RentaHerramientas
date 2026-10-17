package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.ChecklistItemView;
import com.rentatools.rental.application.dto.CreateChecklistItemCommand;
import com.rentatools.rental.application.dto.UpdateChecklistItemCommand;
import com.rentatools.shared.domain.Actor;

/**
 * Caso de uso para la administración del catálogo de checklist de inspección (solo {@code ADMIN}).
 */
public interface ManageChecklistUseCase {

    ChecklistItemView createItem(Actor actor, CreateChecklistItemCommand command);

    ChecklistItemView updateItem(Actor actor, int itemId, UpdateChecklistItemCommand command);
}
