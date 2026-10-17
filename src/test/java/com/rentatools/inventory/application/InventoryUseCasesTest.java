package com.rentatools.inventory.application;

import com.rentatools.inventory.application.InventoryTestFakes.InMemoryCatalogReadRepository;
import com.rentatools.inventory.application.InventoryTestFakes.InMemoryCategoryRepository;
import com.rentatools.inventory.application.InventoryTestFakes.InMemoryFileStorage;
import com.rentatools.inventory.application.InventoryTestFakes.InMemoryProviderVerificationPort;
import com.rentatools.inventory.application.InventoryTestFakes.InMemoryToolRepository;
import com.rentatools.inventory.application.InventoryTestFakes.InMemoryToolUnitRepository;
import com.rentatools.inventory.application.dto.CatalogSearchCriteria;
import com.rentatools.inventory.application.dto.CatalogToolSummaryView;
import com.rentatools.inventory.application.dto.CategoryView;
import com.rentatools.inventory.application.dto.ChangeUnitStatusCommand;
import com.rentatools.inventory.application.dto.CreateCategoryCommand;
import com.rentatools.inventory.application.dto.CreateToolCommand;
import com.rentatools.inventory.application.dto.RegisterToolUnitCommand;
import com.rentatools.inventory.application.dto.SetToolRatesCommand;
import com.rentatools.inventory.application.dto.ToolImageView;
import com.rentatools.inventory.application.dto.ToolUnitView;
import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.inventory.application.dto.UpdateCategoryCommand;
import com.rentatools.inventory.application.dto.UpdateToolCommand;
import com.rentatools.inventory.application.dto.UploadToolImageCommand;
import com.rentatools.inventory.application.service.CatalogQueryService;
import com.rentatools.inventory.application.service.CategoryService;
import com.rentatools.inventory.application.service.ToolImageService;
import com.rentatools.inventory.application.service.ToolManagementService;
import com.rentatools.inventory.application.service.ToolUnitService;
import com.rentatools.inventory.domain.exception.CategoryCycleException;
import com.rentatools.inventory.domain.exception.DuplicateSerialNumberException;
import com.rentatools.inventory.domain.exception.ImageLimitExceededException;
import com.rentatools.inventory.domain.exception.NotToolOwnerException;
import com.rentatools.inventory.domain.exception.ToolNotPublishableException;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.RatePeriod;
import com.rentatools.inventory.domain.model.tool.ToolRate;
import com.rentatools.inventory.domain.model.tool.ToolStatus;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.application.exception.ConflictException;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de los 16 casos de uso y consultas de {@code inventory} con fakes en memoria.
 */
class InventoryUseCasesTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-15T10:00:00Z");

    private InMemoryCategoryRepository categoryRepository;
    private InMemoryToolRepository toolRepository;
    private InMemoryToolUnitRepository unitRepository;
    private InMemoryProviderVerificationPort providerVerificationPort;
    private InMemoryFileStorage fileStorage;
    private InMemoryCatalogReadRepository catalogReadRepository;

    private CategoryService categoryService;
    private ToolManagementService toolManagementService;
    private ToolImageService toolImageService;
    private ToolUnitService toolUnitService;
    private CatalogQueryService catalogQueryService;

    private Actor adminActor;
    private Actor approvedProviderActor;
    private Actor otherProviderActor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        categoryRepository = new InMemoryCategoryRepository();
        toolRepository = new InMemoryToolRepository();
        unitRepository = new InMemoryToolUnitRepository();
        providerVerificationPort = new InMemoryProviderVerificationPort();
        fileStorage = new InMemoryFileStorage();
        catalogReadRepository = new InMemoryCatalogReadRepository(toolRepository, unitRepository);

        categoryService = new CategoryService(categoryRepository, clock);
        toolManagementService = new ToolManagementService(
                toolRepository,
                categoryRepository,
                unitRepository,
                providerVerificationPort,
                clock
        );
        toolImageService = new ToolImageService(toolRepository, fileStorage, clock);
        toolUnitService = new ToolUnitService(toolRepository, unitRepository, clock);
        catalogQueryService = new CatalogQueryService(
                catalogReadRepository,
                toolRepository,
                unitRepository
        );

        adminActor = new Actor(Ids.newId(), Set.of(ActorRole.ADMIN));
        approvedProviderActor = new Actor(Ids.newId(), Set.of(ActorRole.PROVEEDOR));
        otherProviderActor = new Actor(Ids.newId(), Set.of(ActorRole.PROVEEDOR));

        providerVerificationPort.approve(ProviderId.of(approvedProviderActor.userId()));
        providerVerificationPort.approve(ProviderId.of(otherProviderActor.userId()));
    }

    @Test
    void categoryServiceEnforcesUniqueSiblingNamesAndPreventsHierarchyCycles() {
        CategoryView root = categoryService.createCategory(
                adminActor,
                new CreateCategoryCommand(null, "Construcción", "Equipos de construcción")
        );
        CategoryView child = categoryService.createCategory(
                adminActor,
                new CreateCategoryCommand(root.id(), "Demolición", "Martillos y rompedores")
        );
        CategoryView grandChild = categoryService.createCategory(
                adminActor,
                new CreateCategoryCommand(child.id(), "Rotomartillos", "Rotomartillos SDS")
        );

        // Duplicate sibling name under same parent fails
        assertThatThrownBy(() -> categoryService.createCategory(
                adminActor,
                new CreateCategoryCommand(root.id(), "demolición", "Duplicada")
        )).isInstanceOf(ConflictException.class);

        // Introducing a cycle (setting root's parent to grandChild) fails with CategoryCycleException
        assertThatThrownBy(() -> categoryService.updateCategory(
                adminActor,
                new UpdateCategoryCommand(root.id(), grandChild.id(), "Construcción", "Desc", true)
        )).isInstanceOf(CategoryCycleException.class);

        assertThat(categoryService.listCategories(true)).hasSize(3);
    }

    @Test
    void toolPublicationLifecycleAndOwnershipAreEnforced() {
        CategoryView cat = categoryService.createCategory(
                adminActor,
                new CreateCategoryCommand(null, "Carpintería", "Herramientas de madera")
        );

        ToolView created = toolManagementService.createTool(
                approvedProviderActor,
                new CreateToolCommand(
                        "Sierra Circular 7-1/4",
                        "Makita",
                        "HS7600",
                        "Sierra circular profesional",
                        Money.of(120_000),
                        Set.of(cat.id()),
                        List.of(new ToolRate(RatePeriod.WEEK, Money.of(200_000)))
                )
        );
        assertThat(created.status()).isEqualTo(ToolStatus.DRAFT);

        // Other provider cannot update or publish this tool
        assertThatThrownBy(() -> toolManagementService.updateTool(
                otherProviderActor,
                new UpdateToolCommand(
                        created.id(),
                        "Sierra Modificada",
                        "Makita",
                        "HS7600",
                        "Desc",
                        Money.of(120_000),
                        Set.of(cat.id())
                )
        )).isInstanceOf(NotToolOwnerException.class);

        // Cannot publish yet: missing DAY rate and missing AVAILABLE unit
        assertThatThrownBy(() -> toolManagementService.publishTool(approvedProviderActor, created.id()))
                .isInstanceOf(ToolNotPublishableException.class)
                .hasMessageContaining("DAY rate");

        // Configure DAY and WEEK rates
        toolManagementService.setToolRates(
                approvedProviderActor,
                new SetToolRatesCommand(
                        created.id(),
                        List.of(
                                new ToolRate(RatePeriod.DAY, Money.of(40_000)),
                                new ToolRate(RatePeriod.WEEK, Money.of(210_000))
                        )
                )
        );

        // Still cannot publish: 0 AVAILABLE units
        assertThatThrownBy(() -> toolManagementService.publishTool(approvedProviderActor, created.id()))
                .isInstanceOf(ToolNotPublishableException.class)
                .hasMessageContaining("AVAILABLE unit");

        // Register a unit
        ToolUnitView unit = toolUnitService.registerToolUnit(
                approvedProviderActor,
                new RegisterToolUnitCommand(
                        created.id(),
                        "MAK-HS7600-001",
                        LocalDate.of(2026, 2, 10),
                        "Unidad en perfecto estado"
                )
        );
        assertThat(unit.status()).isEqualTo(UnitStatus.AVAILABLE);

        // Duplicate serial number on same tool fails
        assertThatThrownBy(() -> toolUnitService.registerToolUnit(
                approvedProviderActor,
                new RegisterToolUnitCommand(
                        created.id(),
                        "MAK-HS7600-001",
                        LocalDate.of(2026, 2, 11),
                        "Duplicada"
                )
        )).isInstanceOf(DuplicateSerialNumberException.class);

        // If provider loses approval, cannot publish
        providerVerificationPort.revoke(ProviderId.of(approvedProviderActor.userId()));
        assertThatThrownBy(() -> toolManagementService.publishTool(approvedProviderActor, created.id()))
                .isInstanceOf(ToolNotPublishableException.class)
                .hasMessageContaining("not APPROVED");

        // Restore provider approval -> publish succeeds
        providerVerificationPort.approve(ProviderId.of(approvedProviderActor.userId()));
        ToolView published = toolManagementService.publishTool(approvedProviderActor, created.id());
        assertThat(published.status()).isEqualTo(ToolStatus.ACTIVE);
        assertThat(published.availableUnitsCount()).isEqualTo(1L);

        // Search catalog returns the active tool
        PageResult<CatalogToolSummaryView> searchResult = catalogQueryService.searchCatalog(
                new CatalogSearchCriteria("Sierra", cat.id(), Money.of(10_000), Money.of(50_000), null),
                new PageQuery(0, 10)
        );
        assertThat(searchResult.items()).hasSize(1);
        assertThat(searchResult.items().get(0).name()).isEqualTo("Sierra Circular 7-1/4");

        // Admin can deactivate the tool
        ToolView deactivated = toolManagementService.deactivateTool(adminActor, created.id());
        assertThat(deactivated.status()).isEqualTo(ToolStatus.INACTIVE);
    }

    @Test
    void toolImageServiceHandlesUploadRemoveReorderAndMax10Limit() {
        ToolView tool = toolManagementService.createTool(
                approvedProviderActor,
                new CreateToolCommand(
                        "Compresor de Aire 50L",
                        "Campbell Hausfeld",
                        "CH-50",
                        "Compresor lubricado",
                        Money.of(200_000),
                        Set.of(),
                        List.of(new ToolRate(RatePeriod.DAY, Money.of(55_000)))
                )
        );

        ToolImageView img1 = toolImageService.uploadToolImage(
                approvedProviderActor,
                new UploadToolImageCommand(
                        tool.id(),
                        "frente.jpg",
                        "image/jpeg",
                        2048L,
                        new ByteArrayInputStream(new byte[]{1, 2, 3})
                )
        );
        ToolImageView img2 = toolImageService.uploadToolImage(
                approvedProviderActor,
                new UploadToolImageCommand(
                        tool.id(),
                        "lateral.png",
                        "image/png",
                        4096L,
                        new ByteArrayInputStream(new byte[]{4, 5, 6})
                )
        );

        assertThat(img1.position()).isZero();
        assertThat(img2.position()).isEqualTo(1);
        assertThat(fileStorage.exists(img1.storageKey())).isTrue();

        // Reorder so img2 becomes cover (position 0)
        List<ToolImageView> reordered = toolImageService.reorderToolImages(
                approvedProviderActor,
                tool.id(),
                List.of(img2.id(), img1.id())
        );
        assertThat(reordered.get(0).id()).isEqualTo(img2.id());
        assertThat(reordered.get(0).position()).isZero();
        assertThat(reordered.get(1).id()).isEqualTo(img1.id());
        assertThat(reordered.get(1).position()).isEqualTo(1);

        // Remove img2 and verify storage deletion and position compaction
        toolImageService.removeToolImage(approvedProviderActor, tool.id(), img2.id());
        assertThat(fileStorage.exists(img2.storageKey())).isFalse();

        ToolView detail = catalogQueryService.getToolDetail(tool.id());
        assertThat(detail.images()).hasSize(1);
        assertThat(detail.images().get(0).id()).isEqualTo(img1.id());
        assertThat(detail.images().get(0).position()).isZero();

        // Upload 9 more images to reach 10, then verify 11th throws ImageLimitExceededException
        for (int i = 0; i < 9; i++) {
            toolImageService.uploadToolImage(
                    approvedProviderActor,
                    new UploadToolImageCommand(
                            tool.id(),
                            "extra-" + i + ".webp",
                            "image/webp",
                            1024L,
                            new ByteArrayInputStream(new byte[]{7})
                    )
            );
        }

        assertThatThrownBy(() -> toolImageService.uploadToolImage(
                approvedProviderActor,
                new UploadToolImageCommand(
                        tool.id(),
                        "overflow.jpg",
                        "image/jpeg",
                        1024L,
                        new ByteArrayInputStream(new byte[]{8})
                )
        )).isInstanceOf(ImageLimitExceededException.class);
    }

    @Test
    void unitStatusChangesAndProviderToolsListingWorkCorrectly() {
        Actor unapprovedProvider = new Actor(Ids.newId(), Set.of(ActorRole.PROVEEDOR));
        assertThatThrownBy(() -> toolManagementService.createTool(
                unapprovedProvider,
                new CreateToolCommand(
                        "Generador",
                        "Honda",
                        "EU2200i",
                        "Generador inverter",
                        Money.of(300_000),
                        Set.of(),
                        List.of()
                )
        )).isInstanceOf(ForbiddenException.class);

        ToolView tool = toolManagementService.createTool(
                approvedProviderActor,
                new CreateToolCommand(
                        "Generador Inverter",
                        "Honda",
                        "EU2200i",
                        "Generador portátil silencioso",
                        Money.of(300_000),
                        Set.of(),
                        List.of(new ToolRate(RatePeriod.DAY, Money.of(95_000)))
                )
        );

        ToolUnitView unit = toolUnitService.registerToolUnit(
                approvedProviderActor,
                new RegisterToolUnitCommand(tool.id(), "HON-EU-001", LocalDate.of(2026, 3, 1), "Nueva")
        );

        ToolUnitView inMaintenance = toolUnitService.changeUnitStatus(
                approvedProviderActor,
                new ChangeUnitStatusCommand(tool.id(), unit.id(), UnitStatus.MAINTENANCE, "Cambio de aceite")
        );
        assertThat(inMaintenance.status()).isEqualTo(UnitStatus.MAINTENANCE);

        PageResult<ToolView> providerTools = catalogQueryService.listProviderTools(
                approvedProviderActor,
                new PageQuery(0, 10)
        );
        assertThat(providerTools.items()).hasSize(1);
        assertThat(providerTools.items().get(0).availableUnitsCount()).isZero();
    }
}
