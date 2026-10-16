package com.rentatools.iam.infrastructure.adapter.in.api;

import com.rentatools.iam.api.UserDirectory;
import com.rentatools.iam.api.UserSummary;
import com.rentatools.shared.domain.ActorRole;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Implementación provisional de {@link UserDirectory} basada en SQL directo sobre el esquema V1.
 * Se sustituye en el paso A2 por {@code UserDirectoryAdapter}.
 */
@Component
public class JdbcUserDirectory implements UserDirectory {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserDirectory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserSummary> findById(UUID userId) {
        byte[] idBytes = uuidToBytes(userId);
        List<UserRow> rows = jdbcTemplate.query(
                "SELECT full_name, email, status FROM users WHERE id = ?",
                (rs, rowNum) -> new UserRow(
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("status")
                ),
                (Object) idBytes
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UserRow user = rows.getFirst();
        List<String> roleCodes = jdbcTemplate.queryForList(
                "SELECT role_code FROM user_roles WHERE user_id = ?",
                String.class,
                (Object) idBytes
        );
        Set<ActorRole> roles = EnumSet.noneOf(ActorRole.class);
        for (String code : roleCodes) {
            roles.add(ActorRole.valueOf(code));
        }
        boolean active = "ACTIVE".equals(user.status());
        return Optional.of(new UserSummary(userId, user.fullName(), user.email(), roles, active));
    }

    @Override
    public boolean isActiveClient(UUID userId) {
        return findById(userId)
                .map(u -> u.active() && u.roles().contains(ActorRole.CLIENTE))
                .orElse(false);
    }

    @Override
    public boolean isApprovedProvider(UUID userId) {
        byte[] idBytes = uuidToBytes(userId);
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id AND ur.role_code = 'PROVEEDOR'
                JOIN provider_profiles pp ON pp.user_id = u.id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND pp.approved_at IS NOT NULL
                """,
                Integer.class,
                (Object) idBytes
        );
        return count != null && count > 0;
    }

    public static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private record UserRow(String fullName, String email, String status) {
    }
}
