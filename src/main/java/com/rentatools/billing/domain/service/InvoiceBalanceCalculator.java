package com.rentatools.billing.domain.service;

import com.rentatools.billing.domain.model.invoice.Invoice;
import com.rentatools.billing.domain.model.invoice.InvoiceLine;
import com.rentatools.billing.domain.model.invoice.InvoiceTotals;
import com.rentatools.billing.domain.model.invoice.Payment;
import com.rentatools.billing.domain.model.invoice.PaymentKind;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de dominio que calcula los totales e importes pendientes de una factura:
 * <ul>
 *     <li>{@code subtotal = Σ(quantity × unitPrice)}</li>
 *     <li>{@code tax = round(subtotal × taxRate, 2, HALF_UP)}</li>
 *     <li>{@code total = subtotal + tax}</li>
 *     <li>{@code netPaid = Σ PAYMENT − Σ REFUND}</li>
 *     <li>{@code balance = total − netPaid − retainedDeposit}</li>
 * </ul>
 */
public final class InvoiceBalanceCalculator {

    public InvoiceBalanceCalculator() {
    }

    public Money calculateSubtotal(List<InvoiceLine> lines) {
        Objects.requireNonNull(lines, "lines must not be null");
        Money subtotal = Money.ZERO;
        for (InvoiceLine line : lines) {
            subtotal = subtotal.plus(line.lineTotal());
        }
        return subtotal;
    }

    public Money calculateTax(Money subtotal, BigDecimal taxRate) {
        Objects.requireNonNull(subtotal, "subtotal must not be null");
        Objects.requireNonNull(taxRate, "taxRate must not be null");
        if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessRuleViolation(
                    "INVALID_TAX_RATE",
                    "Tax rate must satisfy 0 <= taxRate < 1, got: " + taxRate
            );
        }
        return subtotal.times(taxRate);
    }

    public Money calculateNetPaid(List<Payment> payments) {
        Objects.requireNonNull(payments, "payments must not be null");
        Money netPaid = Money.ZERO;
        for (Payment payment : payments) {
            if (payment.kind() == PaymentKind.PAYMENT) {
                netPaid = netPaid.plus(payment.amount());
            } else if (payment.kind() == PaymentKind.REFUND) {
                netPaid = netPaid.minus(payment.amount());
            }
        }
        return netPaid;
    }

    public InvoiceTotals calculate(
            List<InvoiceLine> lines,
            List<Payment> payments,
            BigDecimal taxRate,
            Money retainedDeposit
    ) {
        Money retained = (retainedDeposit == null) ? Money.ZERO : retainedDeposit;
        if (retained.isNegative()) {
            throw new BusinessRuleViolation(
                    "INVALID_RETAINED_DEPOSIT",
                    "Retained deposit must not be negative, got: " + retained
            );
        }
        Money subtotal = calculateSubtotal(lines);
        Money tax = calculateTax(subtotal, taxRate);
        Money total = subtotal.plus(tax);
        Money netPaid = calculateNetPaid(payments);
        Money balance = total.minus(netPaid).minus(retained);
        return new InvoiceTotals(subtotal, tax, total, netPaid, retained, balance);
    }

    public InvoiceTotals calculate(Invoice invoice) {
        Objects.requireNonNull(invoice, "invoice must not be null");
        return calculate(invoice.lines(), invoice.payments(), invoice.taxRate(), Money.ZERO);
    }

    public InvoiceTotals calculate(Invoice invoice, Money retainedDeposit) {
        Objects.requireNonNull(invoice, "invoice must not be null");
        return calculate(invoice.lines(), invoice.payments(), invoice.taxRate(), retainedDeposit);
    }
}
