package com.rentatools.inventory.infrastructure;

import com.rentatools.inventory.api.UnitRef;
import com.rentatools.inventory.application.dto.CatalogSearchCriteria;
import com.rentatools.inventory.application.dto.CatalogToolSummaryView;
import com.rentatools.inventory.application.port.out.CatalogReadRepository;
import com.rentatools.inventory.application.port.out.CategoryRepository;
import com.rentatools.inventory.application.port.out.ToolRepository;
import com.rentatools.inventory.domain.model.category.Category;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.inventory.domain.model.tool.ProviderId;
import com.rentatools.inventory.domain.model.tool.RatePeriod;
import com.rentatools.inventory.domain.model.tool.Tool;
import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.tool.ToolImage;
import com.rentatools.inventory.domain.model.tool.ToolImageId;
import com.rentatools.inventory.domain.model.tool.ToolRate;
import com.rentatools.inventory.domain.model.unit.SerialNumber;
import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.CatalogReadAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.CategoryPersistenceAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.ToolPersistenceAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.ToolUnitPersistenceAdapter;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.mapper.InventoryPersistenceMapper;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.support.AbstractMySqlIT;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de persistencia JPA contra {@code V2__inventory.sql} en MySQL 8.4 (Testcontainers):
 * inserción, claves foráneas, reordenamiento de imágenes con desplazamiento {@code position + 1000},
 * búsqueda FULLTEXT en {@link CatalogReadAdapter} y bloqueo concurrente {@code SELECT ... FOR UPDATE}
 * en {@link ToolUnitPersistenceAdapter#lockOperationalUnits(UUID)}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        CategoryPersistenceAdapter.class,
        ToolPersistenceAdapter.class,
        ToolUnitPersistenceAdapter.class,
        CatalogReadAdapter.class,
        InventoryPersistenceMapper.class
})
class InventoryPersistenceIT extends AbstractMySqlIT {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private ToolUnitPersistenceAdapter toolUnitPersistenceAdapter;

    @Autowired
    private CatalogReadRepository catalogReadRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Persiste categorías, herramientas con categorías/tarifas/imágenes, reordena imágenes con position+1000 y consulta catálogo")
    void persistsCategoriesToolsRatesImagesReordersImagesAndSearchesCatalog() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID providerUuid = seedProvider("prov.inv.it@rentatools.com", now);

        Category rootCat = categoryRepository.save(Category.create(
                CategoryId.newId(),
                null,
                "Demolición IT",
                "Equipos pesados de demolición",
                now
        ));

        Tool tool = Tool.createDraft(
                ToolId.newId(),
                ProviderId.of(providerUuid),
                "Martillo Demoledor Hexagonal",
                "Bosch",
                "GSH 11 E",
                "Martillo demoledor profesional 1500W para concreto",
                Money.of("250000.00"),
                Set.of(rootCat.id()),
                List.of(
                        new ToolRate(RatePeriod.DAY, Money.of("65000.00")),
                        new ToolRate(RatePeriod.WEEK, Money.of("350000.00"))
                ),
                now
        );

        ToolImageId img1 = ToolImageId.newId();
        ToolImageId img2 = ToolImageId.newId();
        ToolImageId img3 = ToolImageId.newId();
        tool.addImage(img1, "tools/" + tool.id() + "/img1.jpg", "image/jpeg", 12_000L, now);
        tool.addImage(img2, "tools/" + tool.id() + "/img2.png", "image/png", 18_000L, now);
        tool.addImage(img3, "tools/" + tool.id() + "/img3.webp", "image/webp", 15_000L, now);

        Tool savedDraft = toolRepository.save(tool);
        assertThat(savedDraft.images()).extracting(ToolImage::id).containsExactly(img1, img2, img3);
        assertThat(savedDraft.images()).extracting(ToolImage::position).containsExactly(0, 1, 2);

        // Reordenar imágenes (img3 pasa a posición 0 = portada) verificando que position + 1000 evita colisión UNIQUE
        savedDraft.reorderImages(List.of(img3, img1, img2), now.plusSeconds(5));
        Tool reordered = toolRepository.save(savedDraft);
        assertThat(reordered.images()).extracting(ToolImage::id).containsExactly(img3, img1, img2);
        assertThat(reordered.images()).extracting(ToolImage::position).containsExactly(0, 1, 2);
        assertThat(reordered.coverImage().orElseThrow().id()).isEqualTo(img3);

        // Registrar 2 unidades físicas y publicar la herramienta
        ToolUnit u1 = toolUnitPersistenceAdapter.save(ToolUnit.register(
                ToolUnitId.newId(),
                reordered.id(),
                SerialNumber.of("GSH11-IT-001"),
                LocalDate.of(2026, 1, 10),
                "Unidad 1",
                now
        ));
        toolUnitPersistenceAdapter.save(ToolUnit.register(
                ToolUnitId.newId(),
                reordered.id(),
                SerialNumber.of("GSH11-IT-002"),
                LocalDate.of(2026, 1, 10),
                "Unidad 2",
                now
        ));

        long availableUnits = toolUnitPersistenceAdapter.countByToolIdAndStatus(
                reordered.id(),
                com.rentatools.inventory.domain.model.unit.UnitStatus.AVAILABLE
        );
        assertThat(availableUnits).isEqualTo(2L);

        reordered.publish(availableUnits, true, now.plusSeconds(10));
        toolRepository.save(reordered);

        // Búsqueda en CatalogReadAdapter por texto, categoría y rango de precios
        PageResult<CatalogToolSummaryView> catalogPage = catalogReadRepository.searchActiveTools(
                new CatalogSearchCriteria(
                        "Demoledor",
                        rootCat.id().value(),
                        Money.of("50000.00"),
                        Money.of("80000.00"),
                        null
                ),
                PageQuery.of(0, 10)
        );
        assertThat(catalogPage.items()).extracting(CatalogToolSummaryView::id).contains(reordered.id().value());
        CatalogToolSummaryView summary = catalogPage.items().stream()
                .filter(item -> item.id().equals(reordered.id().value()))
                .findFirst()
                .orElseThrow();
        assertThat(summary.availableUnitsCount()).isEqualTo(2L);
        assertThat(summary.coverImageUrl()).endsWith("img3.webp");
        assertThat(u1.serialNumber().value()).isEqualTo("GSH11-IT-001");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("lockOperationalUnits bloquea filas con SELECT ... FOR UPDATE entre transacciones concurrentes")
    void lockOperationalUnitsBlocksConcurrentTransactionUntilFirstCommits() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID providerUuid = seedProvider("prov.lock.it@rentatools.com", now);
        UUID toolUuid = Ids.newId();
        UUID unitUuid = Ids.newId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        txTemplate.executeWithoutResult(status -> {
            Tool tool = Tool.createDraft(
                    ToolId.of(toolUuid),
                    ProviderId.of(providerUuid),
                    "Taladro Lock IT",
                    "DeWalt",
                    "DCD996",
                    "Taladro para prueba de bloqueo",
                    Money.of("100000.00"),
                    Set.of(),
                    List.of(new ToolRate(RatePeriod.DAY, Money.of("40000.00"))),
                    now
            );
            toolRepository.save(tool);
            toolUnitPersistenceAdapter.save(ToolUnit.register(
                    ToolUnitId.of(unitUuid),
                    ToolId.of(toolUuid),
                    SerialNumber.of("LOCK-SN-001"),
                    LocalDate.of(2026, 2, 1),
                    "Unidad bloqueable",
                    now
            ));
        });

        CountDownLatch tx1Locked = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);
        AtomicBoolean tx1Committed = new AtomicBoolean(false);
        AtomicBoolean tx2AcquiredAfterTx1Commit = new AtomicBoolean(false);
        AtomicLong tx2WaitMillis = new AtomicLong(0L);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<UnitRef>> future1 = executor.submit(() ->
                    txTemplate.execute(status -> {
                        List<UnitRef> locked = toolUnitPersistenceAdapter.lockOperationalUnits(toolUuid);
                        tx1Locked.countDown();
                        try {
                            tx2Started.await(5, TimeUnit.SECONDS);
                            // Mantener el lock durante 400ms mientras la transacción 2 espera en SELECT ... FOR UPDATE
                            Thread.sleep(400L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        tx1Committed.set(true);
                        return locked;
                    })
            );

            Future<List<UnitRef>> future2 = executor.submit(() -> {
                assertThat(tx1Locked.await(5, TimeUnit.SECONDS)).isTrue();
                tx2Started.countDown();
                long startNanos = System.nanoTime();
                return txTemplate.execute(status -> {
                    List<UnitRef> locked = toolUnitPersistenceAdapter.lockOperationalUnits(toolUuid);
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                    tx2WaitMillis.set(elapsedMs);
                    tx2AcquiredAfterTx1Commit.set(tx1Committed.get());
                    return locked;
                });
            });

            List<UnitRef> res1 = future1.get(10, TimeUnit.SECONDS);
            List<UnitRef> res2 = future2.get(10, TimeUnit.SECONDS);

            assertThat(res1).hasSize(1);
            assertThat(res2).hasSize(1);
            assertThat(tx2AcquiredAfterTx1Commit.get()).isTrue();
            assertThat(tx2WaitMillis.get()).isGreaterThanOrEqualTo(250L);
        } finally {
            executor.shutdownNow();
            txTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM tool_units WHERE id = ?", (Object) toBytes(unitUuid));
                jdbcTemplate.update("DELETE FROM tool_rates WHERE tool_id = ?", (Object) toBytes(toolUuid));
                jdbcTemplate.update("DELETE FROM tools WHERE id = ?", (Object) toBytes(toolUuid));
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", (Object) toBytes(providerUuid));
            });
        }
    }

    private UUID seedProvider(String email, Instant now) {
        UUID providerId = Ids.newId();
        Timestamp ts = Timestamp.from(now);
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, full_name, status, created_at, updated_at, version)
                VALUES (?, ?, '$2a$10$hash', 'Proveedor Inventory IT', 'ACTIVE', ?, ?, 0)
                """,
                toBytes(providerId),
                email,
                ts,
                ts
        );
        return providerId;
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
