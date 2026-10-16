package com.rentatools.shared.domain;

import java.util.Objects;

/**
 * Excepción base para todos los errores de dominio y aplicación.
 * Contiene un código estable de error de negocio y un mensaje descriptivo.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public DomainException(String code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public String code() {
        return code;
    }

    public String getCode() {
        return code;
    }
}
