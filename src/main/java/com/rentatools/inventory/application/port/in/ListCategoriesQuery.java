package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.CategoryView;
import java.util.List;

/**
 * Puerto de entrada para listar las categorías del catálogo.
 */
public interface ListCategoriesQuery {

    List<CategoryView> listCategories( boolean onlyActive);
}
