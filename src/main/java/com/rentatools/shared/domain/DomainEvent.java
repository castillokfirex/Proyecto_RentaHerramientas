package com.rentatools.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato base para los eventos de dominio internos de un contexto acotado.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();
}
