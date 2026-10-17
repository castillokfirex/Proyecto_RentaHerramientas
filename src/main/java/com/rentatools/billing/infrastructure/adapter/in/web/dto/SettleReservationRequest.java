package com.rentatools.billing.infrastructure.adapter.in.web.dto;

import com.rentatools.billing.domain.model.invoice.PaymentMethod;
import jakarta.validation.constraints.Size;

/**
 * Payload opcional de entrada para liquidar la garantía de una reserva ({@code POST /settle}).
 */
public record SettleReservationRequest(
        PaymentMethod refundMethod,

        @Size(max = 100, message = "refundReference must not exceed 100 characters")
        String refundReference
) {
}
