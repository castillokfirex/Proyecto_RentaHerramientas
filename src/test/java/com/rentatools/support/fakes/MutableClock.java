package com.rentatools.support.fakes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Reloj mutable determinístico para pruebas unitarias y de integración.
 */
public class MutableClock extends Clock {

    private Instant currentInstant;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this.currentInstant = Objects.requireNonNull(initialInstant, "initialInstant must not be null");
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
    }

    public MutableClock(Instant initialInstant) {
        this(initialInstant, ZoneOffset.UTC);
    }

    public static MutableClock fixedAt(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant), ZoneOffset.UTC);
    }

    public void setInstant(Instant newInstant) {
        this.currentInstant = Objects.requireNonNull(newInstant, "newInstant must not be null");
    }

    public void advance(Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        this.currentInstant = this.currentInstant.plus(duration);
    }

    public void advanceDays(long days) {
        advance(Duration.ofDays(days));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(this.currentInstant, zone);
    }

    @Override
    public Instant instant() {
        return currentInstant;
    }
}
