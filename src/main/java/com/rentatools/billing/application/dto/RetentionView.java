package com.rentatools.billing.application.dto;

import com.rentatools.billing.domain.model.deposit.Retention;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * DTO de lectura para una retención de garantía aplicada sobre una factura SETTLEMENT.
 */
public record RetentionView(
        UUID invoiceId,
        BigDecimal amount,
        Instant appliedAt
) {

    public static RetentionView from(Retention retention) {
        Objects.requireNonNull(retention, "retention must not be null");
        return new RetentionView(
                retention.invoiceId().value(),
                retention.amount().amount(),
                retention.appliedAt()
        );
    }
}
