package com.rentatools.rental.infrastructure.adapter.in.web;

import com.rentatools.rental.application.dto.ChecklistItemView;
import com.rentatools.rental.application.port.in.ListChecklistItemsQuery;
import com.rentatools.rental.application.port.in.ManageChecklistUseCase;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.ChecklistItemRequest;
import com.rentatools.rental.infrastructure.adapter.in.web.dto.ChecklistItemResponse;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la administración y configuración de ítems de checklist de inspección (solo {@code ADMIN}).
 */
@RestController
@RequestMapping("/api/v1/admin/checklist-items")
@PreAuthorize("hasRole('ADMIN')")
public class AdminChecklistController {

    private final ManageChecklistUseCase manageChecklistUseCase;
    private final ListChecklistItemsQuery listChecklistItemsQuery;

    public AdminChecklistController(
            ManageChecklistUseCase manageChecklistUseCase,
            ListChecklistItemsQuery listChecklistItemsQuery
    ) {
        this.manageChecklistUseCase = Objects.requireNonNull(manageChecklistUseCase, "manageChecklistUseCase must not be null");
        this.listChecklistItemsQuery = Objects.requireNonNull(listChecklistItemsQuery, "listChecklistItemsQuery must not be null");
    }

    @GetMapping
    public List<ChecklistItemResponse> listItems(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly
    ) {
        return listChecklistItemsQuery.listItems(activeOnly).stream()
                .map(ChecklistItemResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ChecklistItemResponse getItem(@PathVariable int id) {
        return ChecklistItemResponse.from(listChecklistItemsQuery.getItem(id));
    }

    @PostMapping
    public ResponseEntity<ChecklistItemResponse> createItem(
            @CurrentActor Actor actor,
            @Valid @RequestBody ChecklistItemRequest.Create request
    ) {
        ChecklistItemView view = manageChecklistUseCase.createItem(actor, request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/v1/admin/checklist-items/" + view.id()))
                .body(ChecklistItemResponse.from(view));
    }

    @PutMapping("/{id}")
    public ChecklistItemResponse updateItem(
            @CurrentActor Actor actor,
            @PathVariable int id,
            @Valid @RequestBody ChecklistItemRequest.Update request
    ) {
        ChecklistItemView view = manageChecklistUseCase.updateItem(actor, id, request.toCommand());
        return ChecklistItemResponse.from(view);
    }
}
