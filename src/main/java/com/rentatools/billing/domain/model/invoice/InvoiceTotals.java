package com.rentatools.billing.domain.model.invoice;

import com.rentatools.shared.domain.Money;

import java.util.Objects;

/**
 * Value Object con los totales calculados de una factura (nunca persistidos en BD por 3FN).
 * <ul>
 *     <li>{@code subtotal = Σ(quantity × unitPrice)}</li>
 *     <li>{@code tax = round(subtotal × taxRate, 2, HALF_UP)}</li>
 *     <li>{@code total = subtotal + tax}</li>
 *     <li>{@code netPaid = Σ PAYMENT − Σ REFUND}</li>
 *     <li>{@code retainedDeposit = retención aplicada sobre la garantía}</li>
 *     <li>{@code balance = total − netPaid − retainedDeposit}</li>
 * </ul>
 */
public record InvoiceTotals(
        Money subtotal,
        Money tax,
        Money total,
        Money netPaid,
        Money retainedDeposit,
        Money balance
) {

    public InvoiceTotals {
        Objects.requireNonNull(subtotal, "subtotal must not be null");
        Objects.requireNonNull(tax, "tax must not be null");
        Objects.requireNonNull(total, "total must not be null");
        Objects.requireNonNull(netPaid, "netPaid must not be null");
        Objects.requireNonNull(retainedDeposit, "retainedDeposit must not be null");
        Objects.requireNonNull(balance, "balance must not be null");
    }

    public boolean isPaid() {
        return balance.isZero();
    }
}
