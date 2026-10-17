package com.rentatools.inventory.infrastructure;

import com.rentatools.iam.api.UserDirectory;
import com.rentatools.inventory.api.RatePeriod;
import com.rentatools.inventory.api.ToolRentalInfo;
import com.rentatools.inventory.api.UnitRef;
import com.rentatools.inventory.application.port.out.ToolRepository;
import com.rentatools.inventory.domain.model.category.Category;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.tool.ToolImageId;
import com.rentatools.inventory.domain.model.tool.ToolRate;
import com.rentatools.inventory.domain.model.unit.SerialNumber;
import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import com.rentatools.inventory.infrastructure.adapter.in.api.ToolCatalogApiAdapter;
import com.rentatools.inventory.infrastructure.adapter.in.api.UnitInventoryApiAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.iam.IamProviderVerificationAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.ToolUnitPersistenceAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.CategoryJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolImageJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolRateJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolUnitJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.mapper.InventoryPersistenceMapper;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pruebas unitarias de los adaptadores de infraestructura y mappers de {@code inventory}.
 */
class InventoryInfrastructureAdaptersTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    void iamProviderVerificationAdapterDelegatesToUserDirectory() {
        UserDirectory userDirectory = mock(UserDirectory.class);
        UUID providerUuid = Ids.newId();
        given(userDirectory.isApprovedProvider(providerUuid)).willReturn(true);

        IamProviderVerificationAdapter adapter = new IamProviderVerificationAdapter(userDirectory);
        assertThat(adapter.isApprovedProvider(ProviderId.of(providerUuid))).isTrue();
        verify(userDirectory).isApprovedProvider(providerUuid);
    }

    @Test
    void toolCatalogApiAdapterMapsActiveToolAndRates() {
        ToolRepository toolRepository = mock(ToolRepository.class);
        ToolId toolId = ToolId.newId();
        ProviderId providerId = ProviderId.of(Ids.newId());

        Tool tool = Tool.createDraft(
                toolId,
                providerId,
                "Sierra Sable",
                "Milwaukee",
                "M18",
                "Sierra inalámbrica",
                Money.of("180000.00"),
                Set.of(),
                List.of(
                        new ToolRate(com.rentatools.inventory.domain.model.tool.RatePeriod.DAY, Money.of("45000.00")),
                        new ToolRate(com.rentatools.inventory.domain.model.tool.RatePeriod.WEEK, Money.of("240000.00"))
                ),
                NOW
        );
        tool.publish(1L, true, NOW);

        given(toolRepository.findById(toolId)).willReturn(Optional.of(tool));

        ToolCatalogApiAdapter adapter = new ToolCatalogApiAdapter(toolRepository);
        Optional<ToolRentalInfo> infoOpt = adapter.findRentableTool(toolId.value());

        assertThat(infoOpt).isPresent();
        ToolRentalInfo info = infoOpt.get();
        assertThat(info.toolId()).isEqualTo(toolId.value());
        assertThat(info.providerId()).isEqualTo(providerId.value());
        assertThat(info.active()).isTrue();
        assertThat(info.depositAmount()).isEqualByComparingTo("180000.00");
        assertThat(info.rates()).containsEntry(RatePeriod.DAY, new BigDecimal("45000.00"));
        assertThat(info.rates()).containsEntry(RatePeriod.WEEK, new BigDecimal("240000.00"));
    }

    @Test
    void unitInventoryApiAdapterLocksFindsAndChangesUnitStates() {
        ToolUnitPersistenceAdapter persistenceAdapter = mock(ToolUnitPersistenceAdapter.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UnitInventoryApiAdapter apiAdapter = new UnitInventoryApiAdapter(persistenceAdapter, clock);

        UUID toolId = Ids.newId();
        UUID unitId = Ids.newId();
        UnitRef ref = new UnitRef(unitId, toolId, "SN-001");
        given(persistenceAdapter.lockOperationalUnits(toolId)).willReturn(List.of(ref));

        assertThat(apiAdapter.lockOperationalUnits(toolId)).containsExactly(ref);

        ToolUnit unit = ToolUnit.register(
                ToolUnitId.of(unitId),
                ToolId.of(toolId),
                SerialNumber.of("SN-001"),
                LocalDate.of(2026, 1, 1),
                "Inicial",
                NOW
        );
        given(persistenceAdapter.findById(ToolUnitId.of(unitId))).willReturn(Optional.of(unit));

        assertThat(apiAdapter.findUnit(unitId)).contains(ref);

        apiAdapter.sendToMaintenance(unitId, "Daño mayor en retorno");
        ArgumentCaptor<ToolUnit> captor = ArgumentCaptor.forClass(ToolUnit.class);
        verify(persistenceAdapter).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(UnitStatus.MAINTENANCE);
        assertThat(captor.getValue().notes()).isEqualTo("Daño mayor en retorno");
    }

    @Test
    void inventoryPersistenceMapperRoundTripsCategoryToolAndUnit() {
        InventoryPersistenceMapper mapper = new InventoryPersistenceMapper();

        Category category = Category.create(CategoryId.newId(), null, "Soldadura", "Equipos de soldadura", NOW);
        CategoryJpaEntity catEntity = new CategoryJpaEntity();
        mapper.updateEntity(category, catEntity);
        catEntity.setVersion(1L);
        Category rehydratedCat = mapper.toDomain(catEntity);
        assertThat(rehydratedCat.id()).isEqualTo(category.id());
        assertThat(rehydratedCat.name()).isEqualTo("Soldadura");

        ToolJpaEntity toolEntity = new ToolJpaEntity();
        UUID toolUuid = Ids.newId();
        UUID providerUuid = Ids.newId();
        toolEntity.setId(toolUuid);
        toolEntity.setProviderId(providerUuid);
        toolEntity.setName("Soldador Inverter");
        toolEntity.setBrand("Lincoln");
        toolEntity.setModel("Invertec");
        toolEntity.setDepositAmount(new BigDecimal("220000.00"));
        toolEntity.setStatus(com.rentatools.inventory.domain.model.tool.ToolStatus.ACTIVE);
        toolEntity.setCreatedAt(NOW);
        toolEntity.setUpdatedAt(NOW);
        toolEntity.setVersion(2L);
        toolEntity.setCategoryIds(Set.of(category.id().value()));

        ToolRateJpaEntity rateEntity = new ToolRateJpaEntity();
        rateEntity.setToolId(toolUuid);
        rateEntity.setPeriod(com.rentatools.inventory.domain.model.tool.RatePeriod.DAY);
        rateEntity.setPrice(new BigDecimal("60000.00"));
        toolEntity.setRates(List.of(rateEntity));

        ToolImageJpaEntity imgEntity = new ToolImageJpaEntity();
        imgEntity.setId(ToolImageId.newId().value());
        imgEntity.setTool(toolEntity);
        imgEntity.setPosition(0);
        imgEntity.setStorageKey("tools/soldador.jpg");
        imgEntity.setContentType("image/jpeg");
        imgEntity.setSizeBytes(4096L);
        imgEntity.setUploadedAt(NOW);
        toolEntity.setImages(List.of(imgEntity));

        Tool domainTool = mapper.toDomain(toolEntity);
        assertThat(domainTool.id().value()).isEqualTo(toolUuid);
        assertThat(domainTool.coverImage()).isPresent();
        assertThat(domainTool.rates()).containsKey(com.rentatools.inventory.domain.model.tool.RatePeriod.DAY);

        ToolUnit unit = ToolUnit.register(
                ToolUnitId.newId(),
                ToolId.of(toolUuid),
                SerialNumber.of("LIN-001"),
                LocalDate.of(2026, 2, 1),
                "OK",
                NOW
        );
        ToolUnitJpaEntity unitEntity = new ToolUnitJpaEntity();
        mapper.updateEntity(unit, unitEntity);
        unitEntity.setVersion(0L);
        ToolUnit rehydratedUnit = mapper.toDomain(unitEntity);
        assertThat(rehydratedUnit.serialNumber().value()).isEqualTo("LIN-001");
    }
}
