package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.CategoryView;
import com.rentatools.inventory.application.dto.CreateCategoryCommand;
import com.rentatools.shared.domain.Actor;

/**
 * Puerto de entrada para la creación de categorías en el catálogo.
 */
public interface CreateCategoryUseCase {

    CategoryView createCategory(Actor actor, CreateCategoryCommand command);
}
