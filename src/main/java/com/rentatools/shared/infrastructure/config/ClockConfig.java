package com.rentatools.shared.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provee el {@link Clock} del sistema con la zona horaria configurada en {@code app.timezone}.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock systemClock(AppProperties appProperties) {
        return Clock.system(ZoneId.of(appProperties.timezone()));
    }
}
