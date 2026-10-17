package com.rentatools.rental.infrastructure.adapter.out.persistence;

import com.rentatools.rental.application.dto.FrequentClientReportView;
import com.rentatools.rental.application.dto.ToolStatusReportView;
import com.rentatools.rental.application.dto.TopToolReportView;
import com.rentatools.rental.application.port.out.RentalReportPort;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia que ejecuta consultas analíticas y reportes nativos vía {@link JdbcTemplate}.
 */
@Component
@Transactional(readOnly = true)
public class RentalReportPersistenceAdapter implements RentalReportPort {

    private final JdbcTemplate jdbcTemplate;

    public RentalReportPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public List<TopToolReportView> findTopTools(int limit) {
        String sql = """
                SELECT t.id AS tool_id, t.name, t.brand, t.model,
                       COUNT(ru.id) AS total_rentals,
                       COALESCE(SUM(DATEDIFF(r.end_date, r.start_date) + 1), 0) AS total_rental_days
                FROM tools t
                JOIN tool_units tu ON tu.tool_id = t.id
                JOIN reservation_units ru ON ru.unit_id = tu.id
                JOIN reservations r ON r.id = ru.reservation_id
                WHERE r.status IN ('CONFIRMED', 'READY_FOR_PICKUP', 'ACTIVE', 'RETURNED', 'CLOSED')
                GROUP BY t.id, t.name, t.brand, t.model
                ORDER BY total_rentals DESC, total_rental_days DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new TopToolReportView(
                        fromBytes(rs.getBytes("tool_id")),
                        rs.getString("name"),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getLong("total_rentals"),
                        rs.getLong("total_rental_days")
                ),
                limit
        );
    }

    @Override
    public List<FrequentClientReportView> findFrequentClients(int limit) {
        String sql = """
                SELECT u.id AS client_id, u.full_name, u.email,
                       COUNT(r.id) AS total_reservations,
                       SUM(CASE WHEN r.status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed_reservations,
                       SUM(CASE WHEN r.status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_reservations,
                       SUM(CASE WHEN r.status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_reservations
                FROM users u
                JOIN reservations r ON r.client_id = u.id
                WHERE r.status IN ('CONFIRMED', 'READY_FOR_PICKUP', 'ACTIVE', 'RETURNED', 'CLOSED')
                GROUP BY u.id, u.full_name, u.email
                ORDER BY (SUM(CASE WHEN r.status IN ('CONFIRMED', 'CLOSED') THEN 1 ELSE 0 END)) DESC, total_reservations DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new FrequentClientReportView(
                        fromBytes(rs.getBytes("client_id")),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getLong("total_reservations"),
                        rs.getLong("confirmed_reservations"),
                        rs.getLong("closed_reservations"),
                        rs.getLong("active_reservations")
                ),
                limit
        );
    }

    @Override
    public ToolStatusReportView getToolStatusSummary() {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM tool_units) AS total_units,
                    (SELECT COUNT(*) FROM tool_units WHERE status = 'AVAILABLE') AS available_units,
                    (SELECT COUNT(*) FROM tool_units WHERE status = 'MAINTENANCE') AS maintenance_units,
                    (SELECT COUNT(*) FROM tool_units WHERE status = 'RETIRED') AS retired_units,
                    (SELECT COUNT(DISTINCT ru.unit_id)
                     FROM reservation_units ru
                     JOIN reservations r ON r.id = ru.reservation_id
                     WHERE r.status IN ('CONFIRMED', 'READY_FOR_PICKUP', 'ACTIVE')) AS active_occupied_units
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new ToolStatusReportView(
                        rs.getLong("total_units"),
                        rs.getLong("available_units"),
                        rs.getLong("maintenance_units"),
                        rs.getLong("retired_units"),
                        rs.getLong("active_occupied_units")
                )
        );
    }

    private static UUID fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
