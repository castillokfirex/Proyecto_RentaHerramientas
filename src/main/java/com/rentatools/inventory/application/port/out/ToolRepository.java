package com.rentatools.inventory.application.port.out;

import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia del agregado {@link Tool}.
 */
public interface ToolRepository {

    Tool save(Tool tool);

    Optional<Tool> findById(ToolId id);

    PageResult<Tool> findByProviderId(ProviderId providerId, PageQuery pageQuery);

    Optional<Tool> findByProviderIdAndNameIgnoreCase(ProviderId providerId, String name);
}
