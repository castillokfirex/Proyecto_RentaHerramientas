package com.rentatools.shared.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Generador de identificadores UUID v7 (RFC 9562) en Java 21 puro.
 * Garantiza ordenación temporal monótona incluso para múltiples llamadas dentro del mismo milisegundo.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Object LOCK = new Object();

    private static long lastTimestampMs = -1L;
    private static int sequence = 0;

    private Ids() {
    }

    /**
     * Genera un nuevo UUID v7 ordenado por tiempo usando el reloj del sistema en UTC.
     */
    public static UUID newId() {
        return generateMonotonic(System.currentTimeMillis());
    }

    /**
     * Alias de {@link #newId()} para generación de UUID v7.
     */
    public static UUID uuidV7() {
        return newId();
    }

    /**
     * Alias de {@link #newId()}.
     */
    public static UUID generate() {
        return newId();
    }

    /**
     * Genera un nuevo UUID v7 utilizando el {@link Clock} suministrado.
     */
    public static UUID newId(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return generateMonotonic(clock.millis());
    }

    /**
     * Genera un UUID v7 para un instante específico.
     */
    public static UUID newId(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return fromTimestampMillis(instant.toEpochMilli(), RANDOM.nextInt(1 << 12));
    }

    /**
     * Extrae la marca de tiempo en milisegundos desde época Unix (48 bits superiores) de un UUID v7.
     */
    public static long extractTimestampMillis(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        return (uuid.getMostSignificantBits() >>> 16) & 0xFFFFFFFFFFFFL;
    }

    /**
     * Extrae el {@link Instant} codificado en un UUID v7.
     */
    public static Instant extractInstant(UUID uuid) {
        return Instant.ofEpochMilli(extractTimestampMillis(uuid));
    }

    private static UUID generateMonotonic(long currentMillis) {
        final long timestampMs;
        final int seq;
        synchronized (LOCK) {
            if (currentMillis > lastTimestampMs) {
                lastTimestampMs = currentMillis;
                sequence = RANDOM.nextInt(1 << 8);
            } else {
                sequence = (sequence + 1) & 0x0FFF;
                if (sequence == 0) {
                    lastTimestampMs++;
                }
            }
            timestampMs = lastTimestampMs;
            seq = sequence;
        }
        return fromTimestampMillis(timestampMs, seq);
    }

    private static UUID fromTimestampMillis(long timestampMs, int twelveBitSeq) {
        long msb = ((timestampMs & 0xFFFFFFFFFFFFL) << 16)
                | 0x7000L // version 7 (0111)
                | (twelveBitSeq & 0x0FFFL);
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL)
                | 0x8000000000000000L; // RFC 9562 variant 2 (10xx)
        return new UUID(msb, lsb);
    }
}
