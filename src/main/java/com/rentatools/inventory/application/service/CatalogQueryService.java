package com.rentatools.inventory.application.service;

import com.rentatools.inventory.application.dto.CatalogSearchCriteria;
import com.rentatools.inventory.application.dto.CatalogToolSummaryView;
import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.inventory.application.port.in.GetToolDetailQuery;
import com.rentatools.inventory.application.port.in.ListProviderToolsQuery;
import com.rentatools.inventory.application.port.in.SearchCatalogQuery;
import com.rentatools.inventory.application.port.out.CatalogReadRepository;
import com.rentatools.inventory.application.port.out.ToolRepository;
import com.rentatools.inventory.application.port.out.ToolUnitRepository;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de aplicación para las consultas públicas y de proveedor sobre el catálogo de herramientas.
 */
public class CatalogQueryService implements
        SearchCatalogQuery,
        GetToolDetailQuery,
        ListProviderToolsQuery {

    private final CatalogReadRepository catalogReadRepository;
    private final ToolRepository toolRepository;
    private final ToolUnitRepository toolUnitRepository;

    public CatalogQueryService(
            CatalogReadRepository catalogReadRepository,
            ToolRepository toolRepository,
            ToolUnitRepository toolUnitRepository
    ) {
        this.catalogReadRepository = Objects.requireNonNull(catalogReadRepository, "catalogReadRepository must not be null");
        this.toolRepository = Objects.requireNonNull(toolRepository, "toolRepository must not be null");
        this.toolUnitRepository = Objects.requireNonNull(toolUnitRepository, "toolUnitRepository must not be null");
    }

    @Override
    public PageResult<CatalogToolSummaryView> searchCatalog(CatalogSearchCriteria criteria, PageQuery pageQuery) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");
        return catalogReadRepository.searchActiveTools(criteria, pageQuery);
    }

    @Override
    public ToolView getToolDetail(UUID toolId) {
        Objects.requireNonNull(toolId, "toolId must not be null");
        ToolId id = ToolId.of(toolId);
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TOOL_NOT_FOUND", "Tool not found: " + id));
        long availableUnits = toolUnitRepository.countByToolIdAndStatus(tool.id(), UnitStatus.AVAILABLE);
        return ToolView.from(tool, availableUnits);
    }

    @Override
    public PageResult<ToolView> listProviderTools(Actor actor, PageQuery pageQuery) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");
        if (!actor.is(ActorRole.PROVEEDOR) && !actor.is(ActorRole.ADMIN)) {
            throw new ForbiddenException("PROVIDER_REQUIRED", "Only PROVEEDOR users can list provider tools");
        }

        ProviderId providerId = ProviderId.of(actor.userId());
        PageResult<Tool> page = toolRepository.findByProviderId(providerId, pageQuery);
        List<ToolView> items = page.items().stream()
                .map(tool -> {
                    long availableUnits = toolUnitRepository.countByToolIdAndStatus(tool.id(), UnitStatus.AVAILABLE);
                    return ToolView.from(tool, availableUnits);
                })
                .toList();

        return new PageResult<>(items, page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
