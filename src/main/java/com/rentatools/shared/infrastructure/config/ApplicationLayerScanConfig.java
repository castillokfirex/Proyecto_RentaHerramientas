package com.rentatools.shared.infrastructure.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Registra como beans los servicios de aplicación y de dominio por convención de paquete
 * sin requerir anotaciones de Spring en las capas {@code domain} ni {@code application}.
 */
@Configuration
@ComponentScan(
        basePackages = "com.rentatools",
        useDefaultFilters = false,
        includeFilters = @Filter(
                type = FilterType.REGEX,
                pattern = "com\\.rentatools\\..*\\.(application\\.service|domain\\.service)\\..*"
        )
)
public class ApplicationLayerScanConfig {
}
