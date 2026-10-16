package com.rentatools.shared.integration;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato base para los eventos de integración entre contextos acotados.
 */
public interface IntegrationEvent {

    UUID eventId();

    Instant occurredAt();
}
