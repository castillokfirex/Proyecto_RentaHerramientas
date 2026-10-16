package com.rentatools.shared.infrastructure.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades generales de configuración de negocio y seguridad de RentaTools ({@code app.*}).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String currency,
        BigDecimal taxRate,
        BigDecimal lateFeeMultiplier,
        String timezone,
        Cors cors,
        Bootstrap bootstrap
) {

    public AppProperties {
        if (currency == null || currency.isBlank()) {
            currency = "COP";
        }
        if (taxRate == null) {
            taxRate = new BigDecimal("0.18");
        }
        if (lateFeeMultiplier == null) {
            lateFeeMultiplier = new BigDecimal("1.5");
        }
        if (timezone == null || timezone.isBlank()) {
            timezone = "America/Lima";
        }
        if (cors == null) {
            cors = new Cors(List.of("http://localhost:3000", "http://localhost:5173", "http://localhost:8080"));
        }
        if (bootstrap == null) {
            bootstrap = new Bootstrap("admin@rentatools.com", "Admin1234!");
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = (allowedOrigins == null) ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public record Bootstrap(String adminEmail, String adminPassword) {
    }
}
