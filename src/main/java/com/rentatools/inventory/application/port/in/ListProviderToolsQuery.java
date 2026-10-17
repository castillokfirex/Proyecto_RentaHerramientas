package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.Actor;

/**
 * Puerto de entrada para listar las herramientas pertenecientes al proveedor autenticado.
 */
public interface ListProviderToolsQuery {

    PageResult<ToolView> listProviderTools(Actor actor, PageQuery pageQuery);
}
