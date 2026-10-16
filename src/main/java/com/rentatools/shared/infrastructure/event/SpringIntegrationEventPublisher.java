package com.rentatools.shared.infrastructure.event;

import com.rentatools.shared.application.port.out.IntegrationEventPublisher;
import com.rentatools.shared.integration.IntegrationEvent;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adaptador que publica eventos de integración en el bus síncrono de Spring (misma transacción).
 */
@Component
public class SpringIntegrationEventPublisher implements IntegrationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public SpringIntegrationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public void publish(IntegrationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        eventPublisher.publishEvent(event);
    }
}
