package com.rentatools.billing.domain.model.deposit;

import com.rentatools.shared.domain.Money;
import java.util.Objects;

/**
 * Resultado inmutable del cálculo de liquidación entre la factura SETTLEMENT y la garantía.
 */
public record SettlementResult(
        Money settlementTotal,
        Money retainedFromDeposit,
        Money refundToClient,
        Money remainingInvoiceBalance
) {
    public SettlementResult {
        Objects.requireNonNull(settlementTotal, "settlementTotal must not be null");
        Objects.requireNonNull(retainedFromDeposit, "retainedFromDeposit must not be null");
        Objects.requireNonNull(refundToClient, "refundToClient must not be null");
        Objects.requireNonNull(remainingInvoiceBalance, "remainingInvoiceBalance must not be null");
    }

    public boolean isFullyCoveredByDeposit() {
        return remainingInvoiceBalance.isZero();
    }
}
