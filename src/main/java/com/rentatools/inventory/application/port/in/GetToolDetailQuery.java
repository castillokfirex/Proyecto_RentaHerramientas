package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.ToolView;
import java.util.UUID;

/**
 * Puerto de entrada para consultar el detalle de una herramienta del catálogo.
 */
public interface GetToolDetailQuery {

    ToolView getToolDetail(UUID toolId);
}
