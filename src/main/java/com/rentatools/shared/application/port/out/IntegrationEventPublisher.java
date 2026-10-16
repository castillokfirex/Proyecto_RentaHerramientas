package com.rentatools.shared.application.port.out;

import com.rentatools.shared.integration.IntegrationEvent;

/**
 * Puerto de salida para publicar eventos de integración entre contextos acotados.
 */
public interface IntegrationEventPublisher {

    void publish(IntegrationEvent event);
}
