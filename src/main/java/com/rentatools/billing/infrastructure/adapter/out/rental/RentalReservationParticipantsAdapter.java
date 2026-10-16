package com.rentatools.billing.infrastructure.adapter.out.rental;

import com.rentatools.billing.application.port.out.ReservationParticipantsPort;
import com.rentatools.rental.api.ReservationDirectory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador anticorrupción (ACL) que implementa {@link ReservationParticipantsPort}
 * consumiendo {@link ReservationDirectory} de {@code rental.api}.
 *
 * <p>Mientras la pista B no haya registrado aún su implementación de {@link ReservationDirectory} (antes de M2),
 * utiliza como respaldo transparente una consulta sobre la tabla {@code reservations}.</p>
 */
@Component
public class RentalReservationParticipantsAdapter implements ReservationParticipantsPort {

    private final ObjectProvider<ReservationDirectory> reservationDirectoryProvider;
    private final JdbcTemplate jdbcTemplate;

    @ org.springframework.beans.factory.annotation.Autowired
    public RentalReservationParticipantsAdapter(
            ObjectProvider<ReservationDirectory> reservationDirectoryProvider,
            JdbcTemplate jdbcTemplate
    ) {
        this.reservationDirectoryProvider = Objects.requireNonNull(
                reservationDirectoryProvider,
                "reservationDirectoryProvider must not be null"
        );
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Constructor directo para pruebas unitarias con {@link ReservationDirectory}.
     */
    public RentalReservationParticipantsAdapter(ReservationDirectory reservationDirectory) {
        Objects.requireNonNull(reservationDirectory, "reservationDirectory must not be null");
        this.reservationDirectoryProvider = new ObjectProvider<>() {
            @Override
            public ReservationDirectory getObject(Object... args) {
                return reservationDirectory;
            }

            @Override
            public ReservationDirectory getIfAvailable() {
                return reservationDirectory;
            }

            @Override
            public ReservationDirectory getIfUnique() {
                return reservationDirectory;
            }

            @Override
            public ReservationDirectory getObject() {
                return reservationDirectory;
            }
        };
        this.jdbcTemplate = null;
    }

    @Override
    public Optional<Participants> findByReservationId(UUID reservationId) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");

        ReservationDirectory directory = reservationDirectoryProvider.getIfAvailable();
        if (directory != null) {
            return directory.findParticipants(reservationId)
                    .map(rp -> new Participants(
                            rp.reservationId(),
                            rp.code(),
                            rp.clientId(),
                            rp.providerId()
                    ));
        }

        if (jdbcTemplate == null) {
            return Optional.empty();
        }

        List<Participants> rows = jdbcTemplate.query(
                "SELECT id, code, client_id, provider_id FROM reservations WHERE id = ?",
                (rs, rowNum) -> new Participants(
                        fromBytes(rs.getBytes("id")),
                        rs.getString("code"),
                        fromBytes(rs.getBytes("client_id")),
                        fromBytes(rs.getBytes("provider_id"))
                ),
                toBytes(reservationId)
        );
        return rows.stream().findFirst();
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
