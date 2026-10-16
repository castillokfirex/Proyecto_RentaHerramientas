package com.rentatools.billing.infrastructure.config;

import com.rentatools.billing.application.BillingSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Propiedades de configuración de {@code billing} tomadas de {@code app.tax-rate} y
 * {@code app.late-fee-multiplier} y expuestas como el record puro {@link BillingSettings}.
 */
@Configuration
@EnableConfigurationProperties(BillingProperties.Props.class)
public class BillingProperties {

    @Bean
    public BillingSettings billingSettings(Props props) {
        return new BillingSettings(props.taxRate(), props.lateFeeMultiplier());
    }

    @ConfigurationProperties(prefix = "app")
    public record Props(
            BigDecimal taxRate,
            BigDecimal lateFeeMultiplier
    ) {
        public Props {
            if (taxRate == null) {
                taxRate = new BigDecimal("0.18");
            }
            if (lateFeeMultiplier == null) {
                lateFeeMultiplier = new BigDecimal("1.50");
            }
        }
    }
}
