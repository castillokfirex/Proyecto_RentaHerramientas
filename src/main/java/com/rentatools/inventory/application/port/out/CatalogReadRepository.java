package com.rentatools.inventory.application.port.out;

import com.rentatools.inventory.application.dto.CatalogSearchCriteria;
import com.rentatools.inventory.application.dto.CatalogToolSummaryView;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;

/**
 * Puerto de salida para consultas optimizadas de lectura del catálogo público de herramientas activas.
 */
public interface CatalogReadRepository {

    PageResult<CatalogToolSummaryView> searchActiveTools(CatalogSearchCriteria criteria, PageQuery pageQuery);
}
