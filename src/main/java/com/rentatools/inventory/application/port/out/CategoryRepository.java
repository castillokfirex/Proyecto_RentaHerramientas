package com.rentatools.inventory.application.port.out;

import com.rentatools.inventory.domain.model.category.Category;
import com.rentatools.inventory.domain.model.category.CategoryId;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de categorías del catálogo ({@link Category}).
 */
public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(CategoryId id);

    List<Category> findAll();

    /**
     * Busca una categoría hermana (mismo {@code parentId}, que puede ser {@code null} para raíces)
     * con el mismo nombre (comparación case-insensitive).
     */
    Optional<Category> findByParentIdAndNameIgnoreCase(CategoryId parentId, String name);
}
