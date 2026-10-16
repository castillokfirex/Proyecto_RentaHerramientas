package com.rentatools.shared.infrastructure.dev;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint exclusivo del perfil {@code dev} para consultar, adelantar o reiniciar el reloj del sistema.
 */
@RestController
@RequestMapping("/api/v1/dev/clock")
@Profile("dev")
public class DevClockController {

    private final AdjustableClock adjustableClock;

    public DevClockController(AdjustableClock adjustableClock) {
        this.adjustableClock = adjustableClock;
    }

    @GetMapping
    public ClockStatusResponse getClock() {
        return currentStatus();
    }

    @PutMapping
    public ClockStatusResponse setClockOffset(@RequestBody ClockOffsetRequest request) {
        adjustableClock.setOffsetDays(request.offsetDays());
        return currentStatus();
    }

    @DeleteMapping
    public ClockStatusResponse resetClock() {
        adjustableClock.reset();
        return currentStatus();
    }

    private ClockStatusResponse currentStatus() {
        return new ClockStatusResponse(
                adjustableClock.instant(),
                LocalDate.now(adjustableClock),
                adjustableClock.getZone().getId(),
                adjustableClock.getOffsetDays()
        );
    }

    public record ClockOffsetRequest(long offsetDays) {
    }

    public record ClockStatusResponse(
            Instant now,
            LocalDate today,
            String timezone,
            long offsetDays
    ) {
    }
}
