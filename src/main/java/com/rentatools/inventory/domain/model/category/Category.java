package com.rentatools.inventory.domain.model.category;

import com.rentatools.inventory.domain.exception.CategoryCycleException;
import com.rentatools.shared.domain.AggregateRoot;
import java.time.Instant;
import java.util.Objects;

/**
 * Agregado raíz que representa una categoría jerárquica del catálogo de herramientas.
 *
 * <p>Invariantes:
 * <ul>
 *   <li>No puede ser su propio padre ({@code parentId != id}) ni formar ciclos en la jerarquía.</li>
 *   <li>El nombre no puede estar en blanco y tiene máximo 80 caracteres.</li>
 *   <li>La descripción (opcional) tiene máximo 500 caracteres.</li>
 * </ul>
 */
public final class Category extends AggregateRoot {

    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final CategoryId id;
    private CategoryId parentId;
    private String name;
    private String description;
    private boolean active;
    private final Instant createdAt;

    private Category(
            CategoryId id,
            CategoryId parentId,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            long version
    ) {
        super(version);
        this.id = Objects.requireNonNull(id, "Category id must not be null");
        String normalizedName = normalizeAndValidateName(name);
        String normalizedDescription = normalizeAndValidateDescription(description);
        if (parentId != null && parentId.equals(id)) {
            throw new CategoryCycleException("Category " + id + " cannot be its own parent");
        }
        this.parentId = parentId;
        this.name = normalizedName;
        this.description = normalizedDescription;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * Crea una nueva categoría activa.
     */
    public static Category create(
            CategoryId id,
            CategoryId parentId,
            String name,
            String description,
            Instant now
    ) {
        return new Category(id, parentId, name, description, true, now, 0L);
    }

    /**
     * Rehidrata una categoría desde la capa de persistencia.
     */
    public static Category rehydrate(
            CategoryId id,
            CategoryId parentId,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            long version
    ) {
        return new Category(id, parentId, name, description, active, createdAt, version);
    }

    /**
     * Actualiza los datos editables de la categoría.
     */
    public void update(CategoryId newParentId, String newName, String newDescription, boolean newActive) {
        if (newParentId != null && newParentId.equals(this.id)) {
            throw new CategoryCycleException("Category " + this.id + " cannot be its own parent");
        }
        this.name = normalizeAndValidateName(newName);
        this.description = normalizeAndValidateDescription(newDescription);
        this.parentId = newParentId;
        this.active = newActive;
    }

    private static String normalizeAndValidateName(String rawName) {
        Objects.requireNonNull(rawName, "Category name must not be null");
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Category name must not exceed " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String normalizeAndValidateDescription(String rawDescription) {
        if (rawDescription == null) {
            return null;
        }
        String trimmed = rawDescription.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Category description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters"
            );
        }
        return trimmed;
    }

    public CategoryId id() {
        return id;
    }

    public CategoryId parentId() {
        return parentId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
