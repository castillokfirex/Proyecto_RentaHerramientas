package com.rentatools.rental.infrastructure.adapter.in.event;

import com.rentatools.rental.application.port.in.CloseReservationUseCase;
import com.rentatools.rental.application.port.in.MarkClearedForDeliveryUseCase;
import com.rentatools.shared.integration.billing.ReservationClearedForDelivery;
import com.rentatools.shared.integration.billing.ReservationSettled;
import java.util.Objects;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada de eventos de integración provenientes del contexto {@code billing}.
 * Escucha las autorizaciones de entrega y liquidaciones para actualizar el ciclo de vida de la reserva.
 */
@Component
public class BillingEventsListener {

    private final MarkClearedForDeliveryUseCase markClearedForDeliveryUseCase;
    private final CloseReservationUseCase closeReservationUseCase;

    public BillingEventsListener(
            MarkClearedForDeliveryUseCase markClearedForDeliveryUseCase,
            CloseReservationUseCase closeReservationUseCase
    ) {
        this.markClearedForDeliveryUseCase = Objects.requireNonNull(markClearedForDeliveryUseCase, "markClearedForDeliveryUseCase must not be null");
        this.closeReservationUseCase = Objects.requireNonNull(closeReservationUseCase, "closeReservationUseCase must not be null");
    }

    @EventListener
    public void onReservationClearedForDelivery(ReservationClearedForDelivery event) {
        Objects.requireNonNull(event, "event must not be null");
        markClearedForDeliveryUseCase.markClearedForDelivery(event.reservationId());
    }

    @EventListener
    public void onReservationSettled(ReservationSettled event) {
        Objects.requireNonNull(event, "event must not be null");
        closeReservationUseCase.closeReservation(event.reservationId());
    }
}
