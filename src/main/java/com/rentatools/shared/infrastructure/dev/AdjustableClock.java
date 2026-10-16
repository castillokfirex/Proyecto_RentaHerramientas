package com.rentatools.shared.infrastructure.dev;

import com.rentatools.shared.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
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

    public AdjustableClock(AppProperties appProperties) {
        this(Clock.system(ZoneId.of(appProperties.timezone())), new AtomicLong(0L));
    }

    private AdjustableClock(Clock baseClock, AtomicLong offsetDays) {
        this.baseClock = Objects.requireNonNull(baseClock);
        this.offsetDays = Objects.requireNonNull(offsetDays);
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
