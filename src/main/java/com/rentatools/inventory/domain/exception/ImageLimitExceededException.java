package com.rentatools.inventory.domain.exception;

import com.rentatools.shared.domain.BusinessRuleViolation;

/**
 * Se lanza cuando se intenta superar el límite máximo de 10 imágenes por herramienta.
 */
public final class ImageLimitExceededException extends BusinessRuleViolation {

    public ImageLimitExceededException(String message) {
        super("IMAGE_LIMIT_EXCEEDED", message);
    }
}
