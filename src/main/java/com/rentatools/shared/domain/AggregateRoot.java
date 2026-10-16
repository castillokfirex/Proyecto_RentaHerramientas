package com.rentatools.shared.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Clase base para todas las raíces de agregado del dominio.
 * Gestiona el registro y extracción de eventos de dominio y la versión de concurrencia optimista.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();
    private long version;

    protected AggregateRoot() {
        this.version = 0L;
    }

    protected AggregateRoot(long version) {
        this.version = version;
    }

    protected final void registerEvent(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        this.domainEvents.add(event);
    }

    public final List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }

    public final List<DomainEvent> domainEvents() {
        return List.copyOf(this.domainEvents);
    }

    public long version() {
        return version;
    }

    public long getVersion() {
        return version;
    }

    protected void setVersion(long version) {
        this.version = version;
    }
}
