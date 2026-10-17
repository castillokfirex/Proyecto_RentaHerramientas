package com.rentatools.rental.infrastructure;

import com.rentatools.rental.application.dto.ReservationUnitView;
import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.rental.application.port.in.CancelReservationUseCase;
import com.rentatools.rental.application.port.in.ConfirmReservationUseCase;
import com.rentatools.rental.application.port.in.CreateReservationUseCase;
import com.rentatools.rental.application.port.in.GetReservationQuery;
import com.rentatools.rental.application.port.in.ListReservationsQuery;
import com.rentatools.rental.application.port.in.QuoteReservationUseCase;
import com.rentatools.rental.application.port.in.RejectReservationUseCase;
import com.rentatools.rental.domain.exception.InsufficientAvailabilityException;
import com.rentatools.rental.domain.exception.NotReservationParticipantException;
import com.rentatools.rental.domain.model.pricing.Quote;
import com.rentatools.rental.domain.model.pricing.QuoteLine;
import com.rentatools.rental.domain.model.reservation.ReservationStatus;
import com.rentatools.rental.infrastructure.adapter.in.web.ProviderReservationController;
import com.rentatools.rental.infrastructure.adapter.in.web.ReservationController;
import com.rentatools.shared.domain.DateRange;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import com.rentatools.shared.infrastructure.config.ClockConfig;
import com.rentatools.shared.infrastructure.security.JwtConfig;
import com.rentatools.shared.infrastructure.security.RestAuthErrorHandlers;
import com.rentatools.shared.infrastructure.security.SecurityConfig;
import com.rentatools.shared.infrastructure.web.CurrentActorArgumentResolver;
import com.rentatools.shared.infrastructure.web.GlobalExceptionHandler;
import com.rentatools.shared.infrastructure.web.WebMvcConfig;
import com.rentatools.support.JwtRequestPostProcessors;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas {@code @WebMvcTest} para {@link ReservationController} y {@link ProviderReservationController}
 * verificando los códigos de respuesta HTTP 200, 400, 401, 403 y 409.
 */
@WebMvcTest(controllers = {
        ReservationController.class,
        ProviderReservationController.class
})
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        RestAuthErrorHandlers.class,
        CurrentActorArgumentResolver.class,
        WebMvcConfig.class,
        GlobalExceptionHandler.class,
        ClockConfig.class
})
@ActiveProfiles("test")
class RentalControllersWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteReservationUseCase quoteReservationUseCase;

    @MockitoBean
    private CreateReservationUseCase createReservationUseCase;

    @MockitoBean
    private GetReservationQuery getReservationQuery;

    @MockitoBean
    private ListReservationsQuery listReservationsQuery;

    @MockitoBean
    private CancelReservationUseCase cancelReservationUseCase;

    @MockitoBean
    private ConfirmReservationUseCase confirmReservationUseCase;

    @MockitoBean
    private RejectReservationUseCase rejectReservationUseCase;

    @Test
    @DisplayName("200 OK en cotización, creación y confirmación de reserva con roles autorizados")
    void returns200OnQuoteCreateAndConfirm() throws Exception {
        UUID clientId = Ids.newId();
        UUID providerId = Ids.newId();
        UUID toolId = Ids.newId();
        UUID reservationId = Ids.newId();
        UUID unitId = Ids.newId();
        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate end = LocalDate.of(2026, 10, 22);

        Quote quote = Quote.of(
                DateRange.of(start, end),
                List.of(QuoteLine.of(toolId, 1, Money.of("50.00"), Money.of("150.00"), Money.of("100.00")))
        );
        when(quoteReservationUseCase.quoteReservation(any(), any())).thenReturn(quote);

        mockMvc.perform(post("/api/v1/reservations/quote")
                        .with(JwtRequestPostProcessors.asClient(clientId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-10-20",
                                  "endDate": "2026-10-22",
                                  "items": [{"toolId": "%s", "quantity": 1}]
                                }
                                """.formatted(toolId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(3))
                .andExpect(jsonPath("$.totalAgreedAmount").value(150.00));

        ReservationView pendingView = sampleView(reservationId, clientId, providerId, unitId, toolId, ReservationStatus.PENDING);
        when(createReservationUseCase.createReservation(any(), any())).thenReturn(pendingView);

        mockMvc.perform(post("/api/v1/reservations")
                        .with(JwtRequestPostProcessors.asClient(clientId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-10-20",
                                  "endDate": "2026-10-22",
                                  "items": [{"toolId": "%s", "quantity": 1}]
                                }
                                """.formatted(toolId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        ReservationView confirmedView = sampleView(reservationId, clientId, providerId, unitId, toolId, ReservationStatus.CONFIRMED);
        when(confirmReservationUseCase.confirmReservation(any(), eq(reservationId))).thenReturn(confirmedView);

        mockMvc.perform(post("/api/v1/reservations/{id}/confirm", reservationId)
                        .with(JwtRequestPostProcessors.asProvider(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("400 Bad Request cuando el payload de reserva es inválido (items vacío o quantity < 1)")
    void returns400OnValidationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .with(JwtRequestPostProcessors.asClient(Ids.newId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-10-20",
                                  "endDate": "2026-10-22",
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("401 Unauthorized sin token y 403 Forbidden por rol incorrecto o no partícipe")
    void returns401And403OnSecurityAndParticipantViolations() throws Exception {
        UUID reservationId = Ids.newId();
        UUID strangerProviderId = Ids.newId();

        // 401 sin token
        mockMvc.perform(get("/api/v1/reservations/{id}", reservationId))
                .andExpect(status().isUnauthorized());

        // 403 CLIENTE intentando confirmar reserva (requiere PROVEEDOR)
        mockMvc.perform(post("/api/v1/reservations/{id}/confirm", reservationId)
                        .with(JwtRequestPostProcessors.asClient(Ids.newId())))
                .andExpect(status().isForbidden());

        // 403 PROVEEDOR ajeno intentando confirmar reserva de otro proveedor
        when(confirmReservationUseCase.confirmReservation(any(), eq(reservationId)))
                .thenThrow(new NotReservationParticipantException(reservationId, strangerProviderId));

        mockMvc.perform(post("/api/v1/reservations/{id}/confirm", reservationId)
                        .with(JwtRequestPostProcessors.asProvider(strangerProviderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_RESERVATION_PARTICIPANT"));
    }

    @Test
    @DisplayName("409 Conflict cuando no hay suficientes unidades disponibles (INSUFFICIENT_AVAILABILITY)")
    void returns409OnInsufficientAvailability() throws Exception {
        UUID toolId = Ids.newId();
        when(createReservationUseCase.createReservation(any(), any()))
                .thenThrow(new InsufficientAvailabilityException(toolId, 2, 0));

        mockMvc.perform(post("/api/v1/reservations")
                        .with(JwtRequestPostProcessors.asClient(Ids.newId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-10-20",
                                  "endDate": "2026-10-22",
                                  "items": [{"toolId": "%s", "quantity": 2}]
                                }
                                """.formatted(toolId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_AVAILABILITY"));
    }

    private static ReservationView sampleView(
            UUID reservationId,
            UUID clientId,
            UUID providerId,
            UUID unitId,
            UUID toolId,
            ReservationStatus status
    ) {
        return new ReservationView(
                reservationId,
                "ABC1234567",
                clientId,
                providerId,
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 22),
                status,
                null,
                List.of(new ReservationUnitView(
                        Ids.newId(),
                        unitId,
                        toolId,
                        "Taladro Percutor",
                        "SN-001",
                        Money.of("50.00"),
                        Money.of("150.00"),
                        Money.of("100.00")
                )),
                Money.of("150.00"),
                Money.of("100.00"),
                Instant.parse("2026-10-17T12:00:00Z"),
                Instant.parse("2026-10-17T12:00:00Z")
        );
    }
}
