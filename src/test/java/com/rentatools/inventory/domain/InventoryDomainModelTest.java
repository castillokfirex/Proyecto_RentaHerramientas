package com.rentatools.inventory.domain;

import com.rentatools.inventory.domain.event.ToolDeactivated;
import com.rentatools.inventory.domain.event.ToolPublished;
import com.rentatools.inventory.domain.event.UnitStatusChanged;
import com.rentatools.inventory.domain.exception.CategoryCycleException;
import com.rentatools.inventory.domain.exception.ImageLimitExceededException;
import com.rentatools.inventory.domain.exception.NotToolOwnerException;
import com.rentatools.inventory.domain.exception.ToolNotPublishableException;
import com.rentatools.inventory.domain.model.category.Category;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.RatePeriod;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.tool.ToolImage;
import com.rentatools.inventory.domain.model.tool.ToolImageId;
import com.rentatools.inventory.domain.model.tool.ToolRate;
import com.rentatools.inventory.domain.model.tool.ToolStatus;
import com.rentatools.inventory.domain.model.unit.SerialNumber;
import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias puras (sin Spring) del modelo de dominio de {@code inventory}.
 */
class InventoryDomainModelTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void categoryCannotBeItsOwnParent() {
        CategoryId id = CategoryId.newId();

        assertThatThrownBy(() -> Category.create(id, id, "Eléctricas", "Herramientas eléctricas", NOW))
                .isInstanceOf(CategoryCycleException.class)
                .hasMessageContaining("own parent");

        Category category = Category.create(id, null, "Eléctricas", "Desc", NOW);
        assertThatThrownBy(() -> category.update(id, "Eléctricas", "Desc", true))
                .isInstanceOf(CategoryCycleException.class);
    }

    @Test
    void toolEnforcesOwnerAndSupportsRatesGreaterThanZero() {
        ProviderId ownerId = ProviderId.of(Ids.newId());
        ProviderId intruderId = ProviderId.of(Ids.newId());
        Actor ownerActor = new Actor(ownerId.value(), Set.of(ActorRole.PROVEEDOR));
        Actor intruderActor = new Actor(intruderId.value(), Set.of(ActorRole.PROVEEDOR));

        Tool tool = Tool.createDraft(
                ToolId.newId(),
                ownerId,
                "Rotomartillo SDS Plus",
                "Bosch",
                "GBH 2-26",
                "Rotomartillo profesional 800W",
                Money.of(150_000),
                Set.of(CategoryId.newId()),
                List.of(
                        new ToolRate(RatePeriod.DAY, Money.of(45_000)),
                        new ToolRate(RatePeriod.WEEK, Money.of(250_000)),
                        new ToolRate(RatePeriod.MONTH, Money.of(850_000))
                ),
                NOW
        );

        tool.assertOwnedBy(ownerActor);
        assertThatThrownBy(() -> tool.assertOwnedBy(intruderActor))
                .isInstanceOf(NotToolOwnerException.class);

        assertThatThrownBy(() -> new ToolRate(RatePeriod.DAY, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    void toolGalleryEnforcesMax10ImagesCoverPosition0AndReorder() {
        Tool tool = Tool.createDraft(
                ToolId.newId(),
                ProviderId.of(Ids.newId()),
                "Taladro Percutor",
                "DeWalt",
                "DCD796",
                "Taladro inalámbrico 20V",
                Money.of(100_000),
                Set.of(),
                List.of(new ToolRate(RatePeriod.DAY, Money.of(35_000))),
                NOW
        );

        List<ToolImageId> addedIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ToolImageId imgId = ToolImageId.newId();
            addedIds.add(imgId);
            ToolImage added = tool.addImage(imgId, "tools/img-" + i + ".jpg", "image/jpeg", 1024L, NOW);
            assertThat(added.position()).isEqualTo(i);
            assertThat(added.isCover()).isEqualTo(i == 0);
        }

        assertThat(tool.coverImage()).isPresent();
        assertThat(tool.coverImage().get().id()).isEqualTo(addedIds.get(0));

        assertThatThrownBy(() -> tool.addImage(ToolImageId.newId(), "tools/img-10.jpg", "image/jpeg", 1024L, NOW))
                .isInstanceOf(ImageLimitExceededException.class);

        // Remove the cover image (index 0) and verify positions compact to 0..8
        ToolImage removed = tool.removeImage(addedIds.get(0), NOW);
        assertThat(removed.id()).isEqualTo(addedIds.get(0));
        assertThat(tool.images()).hasSize(9);
        for (int i = 0; i < tool.images().size(); i++) {
            assertThat(tool.images().get(i).position()).isEqualTo(i);
        }
        assertThat(tool.coverImage()).isPresent();
        assertThat(tool.coverImage().get().id()).isEqualTo(addedIds.get(1));

        // Reorder images in reverse order and check cover becomes the first in the new order
        List<ToolImageId> reversed = new ArrayList<>(tool.images().stream().map(ToolImage::id).toList());
        java.util.Collections.reverse(reversed);
        tool.reorderImages(reversed, NOW);
        assertThat(tool.coverImage().orElseThrow().id()).isEqualTo(reversed.get(0));
        assertThat(tool.images().get(0).position()).isZero();
    }

    @Test
    void toolPublicationRequiresDayRateAvailableUnitAndApprovedProvider() {
        Tool toolWithoutDayRate = Tool.createDraft(
                ToolId.newId(),
                ProviderId.of(Ids.newId()),
                "Pulidora Angular",
                "Makita",
                "GA4530",
                "Pulidora 4-1/2 pulgadas",
                Money.of(80_000),
                Set.of(),
                List.of(new ToolRate(RatePeriod.WEEK, Money.of(180_000))),
                NOW
        );

        // Fails without DAY rate
        assertThatThrownBy(() -> toolWithoutDayRate.publish(1L, true, NOW))
                .isInstanceOf(ToolNotPublishableException.class)
                .hasMessageContaining("DAY rate");

        toolWithoutDayRate.setRates(List.of(new ToolRate(RatePeriod.DAY, Money.of(30_000))), NOW);

        // Fails without AVAILABLE units
        assertThatThrownBy(() -> toolWithoutDayRate.publish(0L, true, NOW))
                .isInstanceOf(ToolNotPublishableException.class)
                .hasMessageContaining("AVAILABLE unit");

        // Fails if provider is not APPROVED
        assertThatThrownBy(() -> toolWithoutDayRate.publish(1L, false, NOW))
                .isInstanceOf(ToolNotPublishableException.class)
                .hasMessageContaining("not APPROVED");

        // Succeeds when all 3 conditions hold
        toolWithoutDayRate.publish(2L, true, NOW);
        assertThat(toolWithoutDayRate.status()).isEqualTo(ToolStatus.ACTIVE);
        assertThat(toolWithoutDayRate.pullDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(ToolPublished.class);

        // Active tool cannot drop DAY rate
        assertThatThrownBy(() -> toolWithoutDayRate.setRates(
                List.of(new ToolRate(RatePeriod.WEEK, Money.of(180_000))),
                NOW
        )).isInstanceOf(ToolNotPublishableException.class);

        // Deactivate emits ToolDeactivated
        toolWithoutDayRate.deactivate(NOW);
        assertThat(toolWithoutDayRate.status()).isEqualTo(ToolStatus.INACTIVE);
        assertThat(toolWithoutDayRate.pullDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(ToolDeactivated.class);
    }

    @Test
    void toolUnitEnforcesStatusesWithoutRentedAndRetiredIsTerminal() {
        assertThat(UnitStatus.values())
                .containsExactly(UnitStatus.AVAILABLE, UnitStatus.MAINTENANCE, UnitStatus.RETIRED);

        ToolUnit unit = ToolUnit.register(
                ToolUnitId.newId(),
                ToolId.newId(),
                SerialNumber.of("SN-BOSCH-001"),
                LocalDate.of(2026, 1, 15),
                "Unidad nueva",
                NOW
        );
        assertThat(unit.status()).isEqualTo(UnitStatus.AVAILABLE);

        unit.changeStatus(UnitStatus.MAINTENANCE, "Cambio de escobillas", NOW);
        assertThat(unit.status()).isEqualTo(UnitStatus.MAINTENANCE);
        assertThat(unit.pullDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(UnitStatusChanged.class);

        unit.changeStatus(UnitStatus.AVAILABLE, "Lista para operar", NOW);
        assertThat(unit.status()).isEqualTo(UnitStatus.AVAILABLE);

        unit.changeStatus(UnitStatus.RETIRED, "Dada de baja", NOW);
        assertThat(unit.status()).isEqualTo(UnitStatus.RETIRED);

        assertThatThrownBy(() -> unit.changeStatus(UnitStatus.AVAILABLE, "Intentar reactivar", NOW))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("terminal");
    }
}
