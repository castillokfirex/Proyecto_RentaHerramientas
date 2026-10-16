package com.rentatools.billing.application.port.in;

import com.rentatools.billing.application.dto.InvoiceView;
import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso para registrar pagos (o reembolsos) sobre una factura y verificar si se cumplen
 * las condiciones para publicar {@code ReservationClearedForDelivery} o {@code ReservationSettled}.
 */
public interface RegisterPaymentUseCase {

    InvoiceView registerPayment(Command command);

    InvoiceView registerRefund(RefundCommand command);

    record Command(
            Actor actor,
            UUID invoiceId,
            Money amount,
            PaymentMethod method,
            String reference
    ) {
        public Command {
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(invoiceId, "invoiceId must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
            Objects.requireNonNull(method, "method must not be null");
        }
    }

    record RefundCommand(
            Actor actor,
            UUID invoiceId,
            Money amount,
            PaymentMethod method,
            String reference
    ) {
        public RefundCommand {
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(invoiceId, "invoiceId must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
            Objects.requireNonNull(method, "method must not be null");
        }
    }
}
