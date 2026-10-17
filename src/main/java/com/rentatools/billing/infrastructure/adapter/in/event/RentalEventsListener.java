package com.rentatools.billing.infrastructure.adapter.in.event;

import com.rentatools.billing.application.port.in.CancelReservationBillingUseCase;
import com.rentatools.billing.application.port.in.OpenRentalBillingUseCase;
import com.rentatools.billing.application.port.in.OpenRentalBillingUseCase.UnitRentalCharge;
import com.rentatools.billing.application.port.in.OpenSettlementUseCase;
import com.rentatools.billing.application.port.in.OpenSettlementUseCase.ReturnedUnitCharge;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.integration.rental.ReservationCancelled;
import com.rentatools.shared.integration.rental.ReservationConfirmed;
import com.rentatools.shared.integration.rental.ReservationReturned;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Adaptador de entrada de eventos de integración provenientes del contexto {@code rental}.
 * Todos los métodos se ejecutan de forma síncrona dentro de la misma transacción del publicador.
 */
@Component
public class RentalEventsListener {

    private final OpenRentalBillingUseCase openRentalBillingUseCase;
    private final CancelReservationBillingUseCase cancelReservationBillingUseCase;
    private final OpenSettlementUseCase openSettlementUseCase;

    public RentalEventsListener(
            OpenRentalBillingUseCase openRentalBillingUseCase,
            CancelReservationBillingUseCase cancelReservationBillingUseCase,
            OpenSettlementUseCase openSettlementUseCase
    ) {
        this.openRentalBillingUseCase = Objects.requireNonNull(
                openRentalBillingUseCase,
                "openRentalBillingUseCase must not be null"
        );
        this.cancelReservationBillingUseCase = Objects.requireNonNull(
                cancelReservationBillingUseCase,
                "cancelReservationBillingUseCase must not be null"
        );
        this.openSettlementUseCase = Objects.requireNonNull(
                openSettlementUseCase,
                "openSettlementUseCase must not be null"
        );
    }

    @EventListener
    public void onReservationConfirmed(ReservationConfirmed event) {
        Objects.requireNonNull(event, "event must not be null");
        List<UnitRentalCharge> units = event.lines().stream()
                .map(line -> new UnitRentalCharge(
                        line.reservationUnitId(),
                        line.toolName(),
                        line.serialNumber(),
                        Money.of(line.agreedAmount()),
                        Money.of(line.depositAmount())
                ))
                .toList();

        openRentalBillingUseCase.openRentalBilling(new OpenRentalBillingUseCase.Command(
                event.reservationId(),
                units
        ));
    }

    @EventListener
    public void onReservationCancelled(ReservationCancelled event) {
        Objects.requireNonNull(event, "event must not be null");
        cancelReservationBillingUseCase.cancelReservationBilling(new CancelReservationBillingUseCase.Command(
                event.reservationId(),
                event.cancelledBy(),
                event.reason()
        ));
    }

    @EventListener
    public void onReservationReturned(ReservationReturned event) {
        Objects.requireNonNull(event, "event must not be null");
        List<ReturnedUnitCharge> returnedUnits = event.units().stream()
                .map(u -> new ReturnedUnitCharge(
                        u.reservationUnitId(),
                        u.toolName(),
                        u.lateDays(),
                        Money.of(u.dailyRate())
                ))
                .toList();

        openSettlementUseCase.openSettlement(new OpenSettlementUseCase.Command(
                event.reservationId(),
                returnedUnits
        ));
    }
}
