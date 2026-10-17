package com.rentatools.rental.domain.model.reservation;

import com.rentatools.shared.domain.BusinessRuleViolation;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;

/**
 * Código legible único de 10 caracteres en alfabeto Crockford Base32 (`CHAR(10)` en `reservations.code`).
 * Excluye los caracteres ambiguos I, L, O y U.
 */
public record ReservationCode(String value) {

    public static final int LENGTH = 10;
    private static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    public ReservationCode {
        Objects.requireNonNull(value, "value must not be null");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != LENGTH) {
            throw new BusinessRuleViolation(
                    "INVALID_RESERVATION_CODE",
                    "Reservation code must have exactly " + LENGTH + " characters"
            );
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (CROCKFORD_ALPHABET.indexOf(c) < 0) {
                throw new BusinessRuleViolation(
                        "INVALID_RESERVATION_CODE",
                        "Reservation code contains invalid Crockford Base32 character: '" + c + "'"
                );
            }
        }
        value = normalized;
    }

    public static ReservationCode of(String value) {
        return new ReservationCode(value);
    }

    public static ReservationCode generate() {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = CROCKFORD_ALPHABET.charAt(RANDOM.nextInt(CROCKFORD_ALPHABET.length()));
        }
        return new ReservationCode(new String(chars));
    }
}
