package com.rentatools.shared.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración para almacenamiento local de archivos.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String location) {

    public StorageProperties {
        if (location == null || location.isBlank()) {
            location = "./target/uploads";
        }
    }
}
