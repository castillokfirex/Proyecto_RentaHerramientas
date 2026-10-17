package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.CatalogSearchCriteria;
import com.rentatools.inventory.application.dto.CatalogToolSummaryView;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;

/**
 * Puerto de entrada para buscar y filtrar herramientas activas en el catálogo público.
 */
public interface SearchCatalogQuery {

    PageResult<CatalogToolSummaryView> searchCatalog(CatalogSearchCriteria criteria, PageQuery pageQuery);
}
