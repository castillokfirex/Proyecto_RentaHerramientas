package com.rentatools.support.fakes;

import com.rentatools.shared.application.port.out.IntegrationEventPublisher;
import com.rentatools.shared.integration.IntegrationEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fake en memoria de {@link IntegrationEventPublisher} para pruebas unitarias de la capa de aplicación.
 */
public class InMemoryIntegrationEventPublisher implements IntegrationEventPublisher {

    private final List<IntegrationEvent> publishedEvents = new ArrayList<>();

    @Override
    public void publish(IntegrationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        publishedEvents.add(event);
    }

    public List<IntegrationEvent> publishedEvents() {
        return List.copyOf(publishedEvents);
    }

    public <T extends IntegrationEvent> List<T> eventsOfType(Class<T> eventType) {
        return publishedEvents.stream()
                .filter(eventType::isInstance)
                .map(eventType::cast)
                .toList();
    }

    public void clear() {
        publishedEvents.clear();
    }
}
