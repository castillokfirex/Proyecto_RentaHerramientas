package com.rentatools.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;

/**
 * Clase base para pruebas de integración y de persistencia contra MySQL 8.4 mediante Testcontainers.
 * Reutiliza un único contenedor MySQL compartido durante la ejecución de la suite de pruebas.
 */
@ActiveProfiles("test")
public abstract class AbstractMySqlIT {

    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("rentatools")
            .withUsername("rentatools")
            .withPassword("rentatools")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-storage-engine=InnoDB",
                    "--default-time-zone=+00:00"
            )
            .withReuse(true);

    static {
        MYSQL.start();
    }
}
