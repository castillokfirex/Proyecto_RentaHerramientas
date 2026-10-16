package com.rentatools.iam.infrastructure.dev;

import com.rentatools.iam.infrastructure.adapter.in.api.JdbcUserDirectory;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra usuarios determinísticos con UUIDs fijos en el perfil {@code dev} para pruebas con Postman y desarrollo.
 */
@Component
@Profile("dev")
public class DevUsersSeeder implements ApplicationRunner {

    public static final UUID ADMIN_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    public static final UUID PROVIDER_1_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    public static final UUID CLIENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    public static final UUID PROVIDER_2_ID = UUID.fromString("01900000-0000-7000-8000-000000000004");

    public static final String DEFAULT_DEV_PASSWORD = "Password123!";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DevUsersSeeder(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String passwordHash = passwordEncoder.encode(DEFAULT_DEV_PASSWORD);
        Instant now = clock.instant();

        seedUser(ADMIN_ID, "admin.dev@rentatools.com", passwordHash, "Admin Dev", "+573000000001", "ADMIN", now);
        seedUser(PROVIDER_1_ID, "proveedor1.dev@rentatools.com", passwordHash, "Proveedor Uno Dev", "+573000000002", "PROVEEDOR", now);
        seedApprovedProviderProfile(PROVIDER_1_ID, "Herramientas del Norte SAS", "900111222-1", ADMIN_ID, now);

        seedUser(CLIENT_ID, "cliente.dev@rentatools.com", passwordHash, "Cliente Dev", "+573000000003", "CLIENTE", now);

        seedUser(PROVIDER_2_ID, "proveedor2.dev@rentatools.com", passwordHash, "Proveedor Dos Dev", "+573000000004", "PROVEEDOR", now);
        seedApprovedProviderProfile(PROVIDER_2_ID, "Equipos Industriales Sur SAS", "900333444-2", ADMIN_ID, now);
    }

    private void seedUser(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            String phone,
            String roleCode,
            Instant now
    ) {
        byte[] idBytes = JdbcUserDirectory.uuidToBytes(id);
        Timestamp ts = Timestamp.from(now);

        jdbcTemplate.update(
                """
                INSERT IGNORE INTO users (id, email, password_hash, full_name, phone, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 0)
                """,
                idBytes, email, passwordHash, fullName, phone, ts, ts
        );

        jdbcTemplate.update(
                """
                INSERT IGNORE INTO user_roles (user_id, role_code)
                VALUES (?, ?)
                """,
                idBytes, roleCode
        );
    }

    private void seedApprovedProviderProfile(
            UUID userId,
            String businessName,
            String taxId,
            UUID approvedBy,
            Instant now
    ) {
        byte[] userBytes = JdbcUserDirectory.uuidToBytes(userId);
        byte[] approverBytes = JdbcUserDirectory.uuidToBytes(approvedBy);
        Timestamp ts = Timestamp.from(now);

        jdbcTemplate.update(
                """
                INSERT IGNORE INTO provider_profiles (user_id, business_name, tax_id, approved_at, approved_by)
                VALUES (?, ?, ?, ?, ?)
                """,
                userBytes, businessName, taxId, ts, approverBytes
        );
    }
}
