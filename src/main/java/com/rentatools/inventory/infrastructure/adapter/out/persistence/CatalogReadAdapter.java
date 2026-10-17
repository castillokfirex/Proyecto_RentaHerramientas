package com.rentatools.inventory.infrastructure.adapter.out.persistence;

import com.rentatools.inventory.application.dto.CatalogSearchCriteria;
import com.rentatools.inventory.application.dto.CatalogToolSummaryView;
import com.rentatools.inventory.application.port.out.CatalogReadRepository;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolImageJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.entity.ToolJpaEntity;
import com.rentatools.inventory.infrastructure.adapter.out.persistence.repository.SpringDataToolRepository;
import com.rentatools.shared.application.PageQuery;
import com.rentatools.shared.application.PageResult;
import com.rentatools.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de lectura optimizado para búsqueda en el catálogo público de herramientas activas
 * utilizando el índice {@code FULLTEXT ft_tools_search (name, brand, model, description)} de
 * {@code V2__inventory.sql}, filtros por categoría y rango de precios, y paginación.
 */
@Repository
@Transactional(readOnly = true)
public class CatalogReadAdapter implements CatalogReadRepository {

    private final EntityManager entityManager;
    private final SpringDataToolRepository toolRepository;

    public CatalogReadAdapter(EntityManager entityManager, SpringDataToolRepository toolRepository) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.toolRepository = Objects.requireNonNull(toolRepository, "toolRepository must not be null");
    }

    @Override
    public PageResult<CatalogToolSummaryView> searchActiveTools(
            CatalogSearchCriteria criteria,
            PageQuery pageQuery
    ) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");

        StringBuilder where = new StringBuilder(" WHERE t.status = 'ACTIVE' ");
        Map<String, Object> params = new LinkedHashMap<>();

        if (criteria.query() != null && !criteria.query().isBlank()) {
            String trimmed = criteria.query().trim();
            String booleanQuery = toBooleanFullTextQuery(trimmed);
            String likePattern = "%" + trimmed.toLowerCase() + "%";
            where.append("""
                     AND (
                         MATCH(t.name, t.brand, t.model, t.description) AGAINST (:booleanQuery IN BOOLEAN MODE)
                         OR LOWER(t.name) LIKE :likePattern
                         OR LOWER(COALESCE(t.brand, '')) LIKE :likePattern
                         OR LOWER(COALESCE(t.model, '')) LIKE :likePattern
                         OR LOWER(COALESCE(t.description, '')) LIKE :likePattern
                     )
                    """);
            params.put("booleanQuery", booleanQuery);
            params.put("likePattern", likePattern);
        }

        if (criteria.categoryId() != null) {
            where.append("""
                     AND EXISTS (
                         SELECT 1 FROM tool_categories tc
                         WHERE tc.tool_id = t.id AND tc.category_id = :categoryId
                     )
                    """);
            params.put("categoryId", toBytes(criteria.categoryId()));
        }

        if (criteria.minDayPrice() != null) {
            where.append(" AND tr.price >= :minDayPrice ");
            params.put("minDayPrice", criteria.minDayPrice().amount());
        }

        if (criteria.maxDayPrice() != null) {
            where.append(" AND tr.price <= :maxDayPrice ");
            params.put("maxDayPrice", criteria.maxDayPrice().amount());
        }

        String fromAndJoin = """
                FROM tools t
                INNER JOIN tool_rates tr ON tr.tool_id = t.id AND tr.period = 'DAY'
                """;

        String countSql = "SELECT COUNT(*) " + fromAndJoin + where;
        Query countQuery = entityManager.createNativeQuery(countSql);
        params.forEach(countQuery::setParameter);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        if (totalElements == 0L) {
            return PageResult.of(List.of(), pageQuery.page(), pageQuery.size(), 0L);
        }

        String selectSql = """
                SELECT
                    t.id,
                    tr.price AS day_price,
                    (SELECT COUNT(*) FROM tool_units u WHERE u.tool_id = t.id AND u.status = 'AVAILABLE') AS available_units
                """
                + fromAndJoin
                + where
                + " ORDER BY t.created_at DESC, t.id ASC LIMIT :limit OFFSET :offset";

        Query selectQuery = entityManager.createNativeQuery(selectSql);
        params.forEach(selectQuery::setParameter);
        selectQuery.setParameter("limit", pageQuery.size());
        selectQuery.setParameter("offset", pageQuery.offset());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = selectQuery.getResultList();

        List<UUID> toolIds = new ArrayList<>(rows.size());
        Map<UUID, BigDecimal> dayPrices = new LinkedHashMap<>();
        Map<UUID, Long> availableCounts = new LinkedHashMap<>();

        for (Object[] row : rows) {
            UUID id = toUuid(row[0]);
            BigDecimal dayPrice = (BigDecimal) row[1];
            long availableCount = ((Number) row[2]).longValue();
            toolIds.add(id);
            dayPrices.put(id, dayPrice);
            availableCounts.put(id, availableCount);
        }

        Map<UUID, ToolJpaEntity> entitiesById = new LinkedHashMap<>();
        for (ToolJpaEntity entity : toolRepository.findAllById(toolIds)) {
            entitiesById.put(entity.getId(), entity);
        }

        List<CatalogToolSummaryView> items = new ArrayList<>(toolIds.size());
        for (UUID toolId : toolIds) {
            ToolJpaEntity entity = entitiesById.get(toolId);
            if (entity == null) {
                continue;
            }
            String coverUrl = resolveCoverUrl(entity);
            Set<UUID> catIds = entity.getCategoryIds() != null
                    ? new LinkedHashSet<>(entity.getCategoryIds())
                    : Set.of();

            items.add(new CatalogToolSummaryView(
                    entity.getId(),
                    entity.getProviderId(),
                    entity.getName(),
                    entity.getBrand(),
                    entity.getModel(),
                    Money.of(entity.getDepositAmount()),
                    Money.of(dayPrices.get(toolId)),
                    coverUrl,
                    catIds,
                    availableCounts.getOrDefault(toolId, 0L)
            ));
        }

        return PageResult.of(items, pageQuery.page(), pageQuery.size(), totalElements);
    }

    private static String resolveCoverUrl(ToolJpaEntity entity) {
        if (entity.getImages() == null || entity.getImages().isEmpty()) {
            return null;
        }
        for (ToolImageJpaEntity img : entity.getImages()) {
            if (img.getPosition() == 0) {
                return "/api/v1/media/tools/" + img.getStorageKey();
            }
        }
        return "/api/v1/media/tools/" + entity.getImages().get(0).getStorageKey();
    }

    private static String toBooleanFullTextQuery(String raw) {
        String sanitized = raw.replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim();
        if (sanitized.isEmpty()) {
            return raw;
        }
        String[] tokens = sanitized.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (!token.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append('+').append(token).append('*');
            }
        }
        return sb.toString();
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID toUuid(Object raw) {
        if (raw instanceof UUID u) {
            return u;
        }
        if (raw instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(raw.toString());
    }
}
