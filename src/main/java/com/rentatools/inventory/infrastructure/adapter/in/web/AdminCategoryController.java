package com.rentatools.inventory.infrastructure.adapter.in.web;

import com.rentatools.inventory.application.dto.CreateCategoryCommand;
import com.rentatools.inventory.application.dto.UpdateCategoryCommand;
import com.rentatools.inventory.application.port.in.CreateCategoryUseCase;
import com.rentatools.inventory.application.port.in.ListCategoriesQuery;
import com.rentatools.inventory.application.port.in.UpdateCategoryUseCase;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.CategoryResponse;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.CreateCategoryRequest;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.UpdateCategoryRequest;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST de administración de categorías del catálogo (exclusivo para {@code ADMIN}).
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final CreateCategoryUseCase createCategoryUseCase;
    private final UpdateCategoryUseCase updateCategoryUseCase;
    private final ListCategoriesQuery listCategoriesQuery;

    public AdminCategoryController(
            CreateCategoryUseCase createCategoryUseCase,
            UpdateCategoryUseCase updateCategoryUseCase,
            ListCategoriesQuery listCategoriesQuery
    ) {
        this.createCategoryUseCase = Objects.requireNonNull(createCategoryUseCase, "createCategoryUseCase must not be null");
        this.updateCategoryUseCase = Objects.requireNonNull(updateCategoryUseCase, "updateCategoryUseCase must not be null");
        this.listCategoriesQuery = Objects.requireNonNull(listCategoriesQuery, "listCategoriesQuery must not be null");
    }

    @GetMapping
    public List<CategoryResponse> listAllCategories() {
        return listCategoriesQuery.listCategories(false).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @PostMapping
    public CategoryResponse createCategory(
            @CurrentActor Actor actor,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        return CategoryResponse.from(createCategoryUseCase.createCategory(
                actor,
                new CreateCategoryCommand(request.parentId(), request.name(), request.description())
        ));
    }

    @PutMapping("/{id}")
    public CategoryResponse updateCategory(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return CategoryResponse.from(updateCategoryUseCase.updateCategory(
                actor,
                new UpdateCategoryCommand(
                        id,
                        request.parentId(),
                        request.name(),
                        request.description(),
                        request.isActiveOrDefault()
                )
        ));
    }

    @DeleteMapping("/{id}")
    public CategoryResponse deleteCategory(
            @CurrentActor Actor actor,
            @PathVariable UUID id
    ) {
        return CategoryResponse.from(updateCategoryUseCase.deactivateCategory(actor, id));
    }
}
