package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.CategoryView;
import com.rentatools.inventory.application.dto.UpdateCategoryCommand;
import com.rentatools.shared.domain.Actor;

/**
 * Puerto de entrada para la actualización de categorías existentes del catálogo.
 */
public interface UpdateCategoryUseCase {

    CategoryView updateCategory(Actor actor, UpdateCategoryCommand command);
}
