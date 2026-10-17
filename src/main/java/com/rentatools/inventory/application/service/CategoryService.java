package com.rentatools.inventory.application.service;

import com.rentatools.inventory.application.dto.CategoryView;
import com.rentatools.inventory.application.dto.CreateCategoryCommand;
import com.rentatools.inventory.application.dto.UpdateCategoryCommand;
import com.rentatools.inventory.application.port.in.CreateCategoryUseCase;
import com.rentatools.inventory.application.port.in.ListCategoriesQuery;
import com.rentatools.inventory.application.port.in.UpdateCategoryUseCase;
import com.rentatools.inventory.application.port.out.CategoryRepository;
import com.rentatools.inventory.domain.exception.CategoryCycleException;
import com.rentatools.inventory.domain.model.category.Category;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.shared.application.exception.ConflictException;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Servicio de aplicación que gestiona el árbol de categorías del catálogo ({@link Category}).
 */
public class CategoryService implements CreateCategoryUseCase, UpdateCategoryUseCase, ListCategoriesQuery {

    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository, Clock clock) {
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public CategoryView createCategory(Actor actor, CreateCategoryCommand command) {
        requireAdmin(actor);
        Objects.requireNonNull(command, "command must not be null");

        CategoryId parentId = command.parentId() != null ? CategoryId.of(command.parentId()) : null;
        if (parentId != null) {
            categoryRepository.findById(parentId)
                    .orElseThrow(() -> new NotFoundException(
                            "CATEGORY_NOT_FOUND",
                            "Parent category not found: " + parentId
                    ));
        }

        String trimmedName = command.name().trim();
        assertUniqueSiblingName(parentId, trimmedName, null);

        Category category = Category.create(
                CategoryId.newId(),
                parentId,
                trimmedName,
                command.description(),
                clock.instant()
        );

        Category saved = categoryRepository.save(category);
        return CategoryView.from(saved);
    }

    @Override
    public CategoryView updateCategory(Actor actor, UpdateCategoryCommand command) {
        requireAdmin(actor);
        Objects.requireNonNull(command, "command must not be null");

        CategoryId categoryId = CategoryId.of(command.categoryId());
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException(
                        "CATEGORY_NOT_FOUND",
                        "Category not found: " + categoryId
                ));

        CategoryId newParentId = command.parentId() != null ? CategoryId.of(command.parentId()) : null;
        if (newParentId != null) {
            assertNoCycle(categoryId, newParentId);
        }

        String trimmedName = command.name().trim();
        assertUniqueSiblingName(newParentId, trimmedName, categoryId);

        category.update(newParentId, trimmedName, command.description(), command.active());
        Category saved = categoryRepository.save(category);
        return CategoryView.from(saved);
    }

    @Override
    public List<CategoryView> listCategories(boolean onlyActive) {
        return categoryRepository.findAll().stream()
                .filter(cat -> !onlyActive || cat.active())
                .map(CategoryView::from)
                .toList();
    }

    private void assertNoCycle(CategoryId targetId, CategoryId candidateParentId) {
        if (targetId.equals(candidateParentId)) {
            throw new CategoryCycleException("Category " + targetId + " cannot be its own parent");
        }
        Set<CategoryId> visited = new HashSet<>();
        visited.add(targetId);

        CategoryId currentId = candidateParentId;
        while (currentId != null) {
            if (!visited.add(currentId)) {
                throw new CategoryCycleException(
                        "Setting parent " + candidateParentId + " on category " + targetId + " introduces a cycle"
                );
            }
            CategoryId lookupId = currentId;
            Category current = categoryRepository.findById(lookupId)
                    .orElseThrow(() -> new NotFoundException(
                            "CATEGORY_NOT_FOUND",
                            "Parent category not found: " + lookupId
                    ));
            currentId = current.parentId();
        }
    }

    private void assertUniqueSiblingName(CategoryId parentId, String trimmedName, CategoryId excludeCategoryId) {
        Optional<Category> existing = categoryRepository.findByParentIdAndNameIgnoreCase(parentId, trimmedName);
        if (existing.isPresent() && !existing.get().id().equals(excludeCategoryId)) {
            throw new ConflictException(
                    "CATEGORY_NAME_ALREADY_EXISTS",
                    "A sibling category with name '" + trimmedName + "' already exists under parent " + parentId
            );
        }
    }

    private static void requireAdmin(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (!actor.is(ActorRole.ADMIN)) {
            throw new ForbiddenException("ADMIN_REQUIRED", "Only ADMIN users can manage categories");
        }
    }
}
