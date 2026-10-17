package com.rentatools.inventory.infrastructure.adapter.in.web;

import com.rentatools.inventory.application.dto.CreateToolCommand;
import com.rentatools.inventory.application.dto.SetToolRatesCommand;
import com.rentatools.inventory.application.dto.UpdateToolCommand;
import com.rentatools.inventory.application.port.in.CreateToolUseCase;
import com.rentatools.inventory.application.port.in.DeactivateToolUseCase;
import com.rentatools.inventory.application.port.in.ListProviderToolsQuery;
import com.rentatools.inventory.application.port.in.PublishToolUseCase;
import com.rentatools.inventory.application.port.in.SetToolRatesUseCase;
import com.rentatools.inventory.application.port.in.UpdateToolUseCase;
import com.rentatools.inventory.domain.model.tool.ToolRate;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.CreateToolRequest;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.SetToolRatesRequest;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.ToolResponse;
import com.rentatools.inventory.infrastructure.adapter.in.web.dto.UpdateToolRequest;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.infrastructure.web.CurrentActor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la gestión de herramientas por parte del proveedor dueño.
 */
@RestController
@RequestMapping({"/api/v1/provider/tools", "/api/v1/tools"})
public class ProviderToolController {

    private final CreateToolUseCase createToolUseCase;
    private final UpdateToolUseCase updateToolUseCase;
    private final SetToolRatesUseCase setToolRatesUseCase;
    private final PublishToolUseCase publishToolUseCase;
    private final DeactivateToolUseCase deactivateToolUseCase;
    private final ListProviderToolsQuery listProviderToolsQuery;

    public ProviderToolController(
            CreateToolUseCase createToolUseCase,
            UpdateToolUseCase updateToolUseCase,
            SetToolRatesUseCase setToolRatesUseCase,
            PublishToolUseCase publishToolUseCase,
            DeactivateToolUseCase deactivateToolUseCase,
            ListProviderToolsQuery listProviderToolsQuery
    ) {
        this.createToolUseCase = Objects.requireNonNull(createToolUseCase, "createToolUseCase must not be null");
        this.updateToolUseCase = Objects.requireNonNull(updateToolUseCase, "updateToolUseCase must not be null");
        this.setToolRatesUseCase = Objects.requireNonNull(setToolRatesUseCase, "setToolRatesUseCase must not be null");
        this.publishToolUseCase = Objects.requireNonNull(publishToolUseCase, "publishToolUseCase must not be null");
        this.deactivateToolUseCase = Objects.requireNonNull(deactivateToolUseCase, "deactivateToolUseCase must not be null");
        this.listProviderToolsQuery = Objects.requireNonNull(listProviderToolsQuery, "listProviderToolsQuery must not be null");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROVEEDOR', 'ADMIN')")
    public PageResult<ToolResponse> listMyTools(
            @CurrentActor Actor actor,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return listProviderToolsQuery.listProviderTools(actor, PageQuery.of(page, size))
                .map(ToolResponse::from);
    }

    @PostMapping
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ToolResponse createTool(
            @CurrentActor Actor actor,
            @Valid @RequestBody CreateToolRequest request
    ) {
        List<ToolRate> domainRates = request.rates() == null
                ? List.of()
                : request.rates().stream()
                        .map(r -> new ToolRate(r.period(), Money.of(r.price())))
                        .toList();

        return ToolResponse.from(createToolUseCase.createTool(
                actor,
                new CreateToolCommand(
                        request.name(),
                        request.brand(),
                        request.model(),
                        request.description(),
                        Money.of(request.depositAmount()),
                        request.categoryIds() != null ? request.categoryIds() : Set.of(),
                        domainRates
                )
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ToolResponse updateTool(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateToolRequest request
    ) {
        return ToolResponse.from(updateToolUseCase.updateTool(
                actor,
                new UpdateToolCommand(
                        id,
                        request.name(),
                        request.brand(),
                        request.model(),
                        request.description(),
                        Money.of(request.depositAmount()),
                        request.categoryIds() != null ? request.categoryIds() : Set.of()
                )
        ));
    }

    @PutMapping("/{id}/rates")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ToolResponse setToolRates(
            @CurrentActor Actor actor,
            @PathVariable UUID id,
            @Valid @RequestBody SetToolRatesRequest request
    ) {
        List<ToolRate> domainRates = request.rates().stream()
                .map(r -> new ToolRate(r.period(), Money.of(r.price())))
                .toList();

        return ToolResponse.from(setToolRatesUseCase.setToolRates(
                actor,
                new SetToolRatesCommand(id, domainRates)
        ));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ToolResponse publishTool(
            @CurrentActor Actor actor,
            @PathVariable UUID id
    ) {
        return ToolResponse.from(publishToolUseCase.publishTool(actor, id));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('PROVEEDOR', 'ADMIN')")
    public ToolResponse deactivateTool(
            @CurrentActor Actor actor,
            @PathVariable UUID id
    ) {
        return ToolResponse.from(deactivateToolUseCase.deactivateTool(actor, id));
    }
}
