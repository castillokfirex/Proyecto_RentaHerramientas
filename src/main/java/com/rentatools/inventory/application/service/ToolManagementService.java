package com.rentatools.inventory.application.service;

import com.rentatools.inventory.application.dto.CreateToolCommand;
import com.rentatools.inventory.application.dto.SetToolRatesCommand;
import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.inventory.application.dto.UpdateToolCommand;
import com.rentatools.inventory.application.port.in.CreateToolUseCase;
import com.rentatools.inventory.application.port.in.DeactivateToolUseCase;
import com.rentatools.inventory.application.port.in.PublishToolUseCase;
import com.rentatools.inventory.application.port.in.SetToolRatesUseCase;
import com.rentatools.inventory.application.port.in.UpdateToolUseCase;
import com.rentatools.inventory.application.port.out.CategoryRepository;
import com.rentatools.inventory.application.port.out.ProviderVerificationPort;
import com.rentatools.inventory.application.port.out.ToolRepository;
import com.rentatools.inventory.application.port.out.ToolUnitRepository;
import com.rentatools.inventory.domain.model.category.Category;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.shared.application.exception.ConflictException;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de aplicación que gestiona el ciclo de vida de una herramienta ({@link Tool}):
 * creación en borrador, actualización, tarifas, publicación y desactivación.
 */
public class ToolManagementService implements
        CreateToolUseCase,
        UpdateToolUseCase,
        SetToolRatesUseCase,
        PublishToolUseCase,
        DeactivateToolUseCase {

    private final ToolRepository toolRepository;
    private final CategoryRepository categoryRepository;
    private final ToolUnitRepository toolUnitRepository;
    private final ProviderVerificationPort providerVerificationPort;
    private final Clock clock;

    public ToolManagementService(
            ToolRepository toolRepository,
            CategoryRepository categoryRepository,
            ToolUnitRepository toolUnitRepository,
            ProviderVerificationPort providerVerificationPort,
            Clock clock
    ) {
        this.toolRepository = Objects.requireNonNull(toolRepository, "toolRepository must not be null");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository must not be null");
        this.toolUnitRepository = Objects.requireNonNull(toolUnitRepository, "toolUnitRepository must not be null");
        this.providerVerificationPort = Objects.requireNonNull(providerVerificationPort, "providerVerificationPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ToolView createTool(Actor actor, CreateToolCommand command) {
        requireProviderRole(actor);
        Objects.requireNonNull(command, "command must not be null");

        ProviderId providerId = ProviderId.of(actor.userId());
        if (!providerVerificationPort.isApprovedProvider(providerId)) {
            throw new ForbiddenException(
                    "PROVIDER_NOT_APPROVED",
                    "Provider " + providerId + " must be APPROVED to register tools"
            );
        }

        String trimmedName = command.name().trim();
        assertUniqueToolNameForProvider(providerId, trimmedName, null);

        Set<CategoryId> categoryIds = resolveAndValidateCategories(command.categoryIds());

        Tool tool = Tool.createDraft(
                ToolId.newId(),
                providerId,
                trimmedName,
                command.brand(),
                command.model(),
                command.description(),
                command.depositAmount(),
                categoryIds,
                command.rates(),
                clock.instant()
        );

        Tool saved = toolRepository.save(tool);
        return ToolView.from(saved, 0L);
    }

    @Override
    public ToolView updateTool(Actor actor, UpdateToolCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Tool tool = loadToolOrThrow(command.toolId());
        tool.assertOwnedBy(actor);

        String trimmedName = command.name().trim();
        assertUniqueToolNameForProvider(tool.providerId(), trimmedName, tool.id());

        Set<CategoryId> categoryIds = resolveAndValidateCategories(command.categoryIds());

        tool.updateDetails(
                trimmedName,
                command.brand(),
                command.model(),
                command.description(),
                command.depositAmount(),
                categoryIds,
                clock.instant()
        );

        Tool saved = toolRepository.save(tool);
        long availableUnits = toolUnitRepository.countByToolIdAndStatus(saved.id(), UnitStatus.AVAILABLE);
        return ToolView.from(saved, availableUnits);
    }

    @Override
    public ToolView setToolRates(Actor actor, SetToolRatesCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Tool tool = loadToolOrThrow(command.toolId());
        tool.assertOwnedBy(actor);

        tool.setRates(command.rates(), clock.instant());

        Tool saved = toolRepository.save(tool);
        long availableUnits = toolUnitRepository.countByToolIdAndStatus(saved.id(), UnitStatus.AVAILABLE);
        return ToolView.from(saved, availableUnits);
    }

    @Override
    public ToolView publishTool(Actor actor, UUID toolId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(toolId, "toolId must not be null");

        Tool tool = loadToolOrThrow(toolId);
        tool.assertOwnedBy(actor);

        long availableUnits = toolUnitRepository.countByToolIdAndStatus(tool.id(), UnitStatus.AVAILABLE);
        boolean providerApproved = providerVerificationPort.isApprovedProvider(tool.providerId());

        tool.publish(availableUnits, providerApproved, clock.instant());

        Tool saved = toolRepository.save(tool);
        return ToolView.from(saved, availableUnits);
    }

    @Override
    public ToolView deactivateTool(Actor actor, UUID toolId) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(toolId, "toolId must not be null");

        Tool tool = loadToolOrThrow(toolId);
        tool.assertOwnedByOrAdmin(actor);

        tool.deactivate(clock.instant());

        Tool saved = toolRepository.save(tool);
        long availableUnits = toolUnitRepository.countByToolIdAndStatus(saved.id(), UnitStatus.AVAILABLE);
        return ToolView.from(saved, availableUnits);
    }

    private Tool loadToolOrThrow(UUID toolId) {
        ToolId id = ToolId.of(toolId);
        return toolRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("TOOL_NOT_FOUND", "Tool not found: " + id));
    }

    private void assertUniqueToolNameForProvider(ProviderId providerId, String name, ToolId excludeToolId) {
        Optional<Tool> existing = toolRepository.findByProviderIdAndNameIgnoreCase(providerId, name);
        if (existing.isPresent() && !existing.get().id().equals(excludeToolId)) {
            throw new ConflictException(
                    "TOOL_NAME_ALREADY_EXISTS",
                    "Provider " + providerId + " already has a tool named '" + name + "'"
            );
        }
    }

    private Set<CategoryId> resolveAndValidateCategories(Set<UUID> rawCategoryIds) {
        Set<CategoryId> result = new LinkedHashSet<>();
        if (rawCategoryIds == null || rawCategoryIds.isEmpty()) {
            return result;
        }
        for (UUID rawId : rawCategoryIds) {
            CategoryId categoryId = CategoryId.of(rawId);
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException(
                            "CATEGORY_NOT_FOUND",
                            "Category not found: " + categoryId
                    ));
            if (!category.active()) {
                throw new BusinessRuleViolation(
                        "INACTIVE_CATEGORY",
                        "Category " + categoryId + " is inactive and cannot be assigned to a tool"
                );
            }
            result.add(categoryId);
        }
        return result;
    }

    private static void requireProviderRole(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (!actor.is(ActorRole.PROVEEDOR)) {
            throw new ForbiddenException("PROVIDER_REQUIRED", "Only PROVEEDOR users can create tools");
        }
    }
}
