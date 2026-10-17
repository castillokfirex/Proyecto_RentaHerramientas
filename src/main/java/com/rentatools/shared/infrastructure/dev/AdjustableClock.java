package com.rentatools.shared.infrastructure.dev;

import com.rentatools.shared.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reloj ajustable en caliente para el perfil {@code dev}, utilizado por Postman para simular
 * el paso de los días (entregas, devoluciones y cálculo de mora).
 */
@Component
@Primary
@Profile("dev")
public class AdjustableClock extends Clock {

    private final Clock baseClock;
    private final AtomicLong offsetDays;

    public AdjustableClock() {
        this(Clock.system(ZoneId.of("America/Bogota")));
    }

    public AdjustableClock(Clock baseClock) {
        this(baseClock, new AtomicLong(0L));
    }

    @Autowired(required = false)
    public AdjustableClock(AppProperties appProperties) {
        this(Clock.system(ZoneId.of(appProperties != null && appProperties.timezone() != null
                ? appProperties.timezone()
                : "America/Bogota")), new AtomicLong(0L));
    }

    public AdjustableClock(Clock baseClock, AtomicLong offsetDays) {
        this.baseClock = Objects.requireNonNull(baseClock, "baseClock must not be null");
        this.offsetDays = Objects.requireNonNull(offsetDays, "offsetDays must not be null");
    }

    public void setOffsetDays(long days) {
        this.offsetDays.set(days);
    }

    public long getOffsetDays() {
        return this.offsetDays.get();
    }

    public void reset() {
        this.offsetDays.set(0L);
    }

    @Override
    public ZoneId getZone() {
        return baseClock.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new AdjustableClock(baseClock.withZone(zone), this.offsetDays);
    }

    @Override
    public Instant instant() {
        return baseClock.instant().plus(Duration.ofDays(offsetDays.get()));
    }
}
