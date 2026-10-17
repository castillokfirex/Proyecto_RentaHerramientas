package com.rentatools.rental.application.service;

import com.rentatools.rental.application.dto.ChecklistItemView;
import com.rentatools.rental.application.dto.CreateChecklistItemCommand;
import com.rentatools.rental.application.dto.UpdateChecklistItemCommand;
import com.rentatools.rental.application.mapper.ChecklistViewMapper;
import com.rentatools.rental.application.port.in.ListChecklistItemsQuery;
import com.rentatools.rental.application.port.in.ManageChecklistUseCase;
import com.rentatools.rental.application.port.out.ChecklistItemRepository;
import com.rentatools.rental.domain.model.checklist.ChecklistItem;
import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de aplicación para la gestión del catálogo de ítems de checklist de inspección.
 */
public class ChecklistService implements ManageChecklistUseCase, ListChecklistItemsQuery {

    private final ChecklistItemRepository checklistItemRepository;

    public ChecklistService(ChecklistItemRepository checklistItemRepository) {
        this.checklistItemRepository = Objects.requireNonNull(checklistItemRepository, "checklistItemRepository must not be null");
    }

    @Override
    public ChecklistItemView createItem(Actor actor, CreateChecklistItemCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        assertAdmin(actor);

        if (checklistItemRepository.findByCode(command.code().trim().toUpperCase()).isPresent()) {
            throw new BusinessRuleViolation(
                    "DUPLICATE_CHECKLIST_CODE",
                    "A checklist item with code '" + command.code().trim().toUpperCase() + "' already exists"
            );
        }

        ChecklistItem item = ChecklistItem.create(command.code(), command.label());
        ChecklistItem saved = checklistItemRepository.save(item);
        return ChecklistViewMapper.toView(saved);
    }

    @Override
    public ChecklistItemView updateItem(Actor actor, int itemId, UpdateChecklistItemCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        assertAdmin(actor);

        ChecklistItem item = loadItemOrThrow(itemId);
        item.update(command.label(), command.active());
        ChecklistItem saved = checklistItemRepository.save(item);
        return ChecklistViewMapper.toView(saved);
    }

    @Override
    public List<ChecklistItemView> listItems(boolean onlyActive) {
        List<ChecklistItem> items = onlyActive
                ? checklistItemRepository.findAllActive()
                : checklistItemRepository.findAll();
        return items.stream()
                .map(ChecklistViewMapper::toView)
                .toList();
    }

    @Override
    public ChecklistItemView getItem(int itemId) {
        return ChecklistViewMapper.toView(loadItemOrThrow(itemId));
    }

    private ChecklistItem loadItemOrThrow(int itemId) {
        return checklistItemRepository.findById(ChecklistItemId.of(itemId))
                .orElseThrow(() -> new NotFoundException("CHECKLIST_ITEM_NOT_FOUND", "Checklist item not found: " + itemId));
    }

    private void assertAdmin(Actor actor) {
        if (!actor.is(ActorRole.ADMIN)) {
            throw new BusinessRuleViolation("FORBIDDEN", "Only administrators can manage checklist items");
        }
    }
}
