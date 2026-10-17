package com.rentatools.rental.infrastructure.adapter.out.persistence;

import com.rentatools.rental.application.port.out.UnitAvailabilityPort;
import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.shared.domain.DateRange;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de infraestructura que implementa {@link UnitAvailabilityPort} consultando las tablas
 * {@code reservation_units}, {@code reservations} e {@code inspections} de {@code V3__rental.sql}.
 *
 * <p>Detecta unidades ocupadas cuando:</p>
 * <ol>
 *   <li>Están en una reserva con estado {@code (PENDING, CONFIRMED, READY_FOR_PICKUP, ACTIVE)}
 *       cuyo rango se solapa con el solicitado ({@code start_date <= :newEnd AND end_date >= :newStart}), o</li>
 *   <li>Están en una reserva con estado {@code ACTIVE} que aún no tiene registrada una inspección de tipo {@code RETURN}
 *       (devolución demorada).</li>
 * </ol>
 */
@Component
@Transactional(readOnly = true)
public class UnitAvailabilityAdapter implements UnitAvailabilityPort {

    private static final String FIND_OCCUPIED_UNITS_SQL = """
            SELECT DISTINCT ru.unit_id
            FROM reservation_units ru
            JOIN reservations r ON r.id = ru.reservation_id
            WHERE ru.unit_id IN (:unitIds)
              AND (
                  (r.status IN ('PENDING', 'CONFIRMED', 'READY_FOR_PICKUP', 'ACTIVE')
                   AND r.start_date <= :newEnd
                   AND r.end_date >= :newStart)
                  OR
                  (r.status = 'ACTIVE'
                   AND NOT EXISTS (
                       SELECT 1
                       FROM inspections i
                       WHERE i.reservation_unit_id = ru.id
                         AND i.type = 'RETURN'
                   ))
              )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UnitAvailabilityAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Set<UnitId> findOccupiedUnits(Collection<UnitId> candidateUnitIds, DateRange dateRange) {
        Objects.requireNonNull(candidateUnitIds, "candidateUnitIds must not be null");
        Objects.requireNonNull(dateRange, "dateRange must not be null");

        if (candidateUnitIds.isEmpty()) {
            return Set.of();
        }

        List<byte[]> binaryIds = new ArrayList<>(candidateUnitIds.size());
        for (UnitId unitId : candidateUnitIds) {
            Objects.requireNonNull(unitId, "unitId must not be null");
            binaryIds.add(toBytes(unitId.value()));
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("unitIds", binaryIds)
                .addValue("newStart", Date.valueOf(dateRange.start()))
                .addValue("newEnd", Date.valueOf(dateRange.end()));

        List<UnitId> rows = jdbcTemplate.query(
                FIND_OCCUPIED_UNITS_SQL,
                params,
                (rs, rowNum) -> UnitId.of(fromBytes(rs.getBytes("unit_id")))
        );

        return Set.copyOf(new LinkedHashSet<>(rows));
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
