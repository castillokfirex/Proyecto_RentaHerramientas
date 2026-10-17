package com.rentatools.rental.infrastructure.adapter.in.api;

import com.rentatools.rental.api.ReservationDirectory;
import com.rentatools.rental.api.ReservationParticipants;
import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de API interna que implementa {@link ReservationDirectory} para exponer a otros contextos
 * (como {@code billing}) los participantes de una reserva.
 */
@Component
@Transactional(readOnly = true)
public class ReservationDirectoryAdapter implements ReservationDirectory {

    private final ReservationRepository reservationRepository;

    public ReservationDirectoryAdapter(ReservationRepository reservationRepository) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository must not be null");
    }

    @Override
    public Optional<ReservationParticipants> findParticipants(UUID reservationId) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        return reservationRepository.findById(ReservationId.of(reservationId))
                .map(r -> new ReservationParticipants(
                        r.id().value(),
                        r.code().value(),
                        r.clientId().value(),
                        r.providerId().value()
                ));
    }
}
