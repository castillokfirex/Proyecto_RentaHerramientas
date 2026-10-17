package com.rentatools.contract;

import com.rentatools.inventory.api.RatePeriod;
import com.rentatools.inventory.api.ToolCatalogApi;
import com.rentatools.inventory.api.ToolRentalInfo;
import com.rentatools.inventory.api.UnitInventoryApi;
import com.rentatools.inventory.api.UnitRef;
import com.rentatools.rental.api.ReservationDirectory;
import com.rentatools.rental.api.ReservationParticipants;
import com.rentatools.shared.integration.rental.ReservationCancelled;
import com.rentatools.shared.integration.rental.ReservationConfirmed;
import com.rentatools.shared.integration.rental.ReservationReturned;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("B0: Verificacion de Contratos Publicos (Java 21 Puro)")
class B0ContractsTest {

    private static final List<Class<?>> ALL_B0_CONTRACT_CLASSES = List.of(
            ReservationConfirmed.class,
            ReservationConfirmed.Line.class,
            ReservationCancelled.class,
            ReservationReturned.class,
            ReservationReturned.ReturnedUnit.class,
            ReservationDirectory.class,
            ReservationParticipants.class,
            RatePeriod.class,
            ToolRentalInfo.class,
            ToolCatalogApi.class,
            UnitRef.class,
            UnitInventoryApi.class
    );

    @Nested
    @DisplayName("Pureza Java 21 y Ausencia de Frameworks")
    class JavaPurityTests {

        @Test
        @DisplayName("Ningun contrato de B0 contiene anotaciones de Spring, Jakarta o Lombok")
        void noFrameworkAnnotationsOnContracts() {
            for (Class<?> clazz : ALL_B0_CONTRACT_CLASSES) {
                for (Annotation annotation : clazz.getAnnotations()) {
                    String annotationPackage = annotation.annotationType().getPackageName();
                    assertThat(annotationPackage)
                            .doesNotStartWith("org.springframework")
                            .doesNotStartWith("jakarta")
                            .doesNotStartWith("lombok")
                            .doesNotStartWith("org.hibernate");
                }
            }
        }

        @Test
        @DisplayName("Todos los contratos son interfaces, records o enums")
        void contractsAreInterfacesRecordsOrEnums() {
            for (Class<?> clazz : ALL_B0_CONTRACT_CLASSES) {
                boolean validKind = clazz.isInterface() || clazz.isRecord() || clazz.isEnum();
                assertThat(validKind)
                        .withFailMessage("La clase %s debe ser interface, record o enum", clazz.getName())
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("shared.integration.rental: Eventos de Integracion")
    class SharedIntegrationRentalTests {

        @Test
        @DisplayName("ReservationConfirmed valida no-nulos y realiza copia inmutable de lineas")
        void reservationConfirmedContract() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now();
            UUID resId = UUID.randomUUID();
            UUID clientId = UUID.randomUUID();
            UUID providerId = UUID.randomUUID();
            UUID confirmedBy = UUID.randomUUID();

            ReservationConfirmed.Line line = new ReservationConfirmed.Line(
                    UUID.randomUUID(),
                    "Rotomartillo SDS Plus",
                    "SN-ROT-001",
                    new BigDecimal("120.00"),
                    new BigDecimal("250.00")
            );

            List<ReservationConfirmed.Line> lines = List.of(line);

            ReservationConfirmed event = new ReservationConfirmed(
                    eventId,
                    now,
                    resId,
                    "RES-2026-0001",
                    clientId,
                    providerId,
                    LocalDate.of(2026, 10, 20),
                    LocalDate.of(2026, 10, 25),
                    confirmedBy,
                    lines
            );

            assertThat(event.eventId()).isEqualTo(eventId);
            assertThat(event.occurredAt()).isEqualTo(now);
            assertThat(event.reservationId()).isEqualTo(resId);
            assertThat(event.reservationCode()).isEqualTo("RES-2026-0001");
            assertThat(event.clientId()).isEqualTo(clientId);
            assertThat(event.providerId()).isEqualTo(providerId);
            assertThat(event.startDate()).isEqualTo(LocalDate.of(2026, 10, 20));
            assertThat(event.endDate()).isEqualTo(LocalDate.of(2026, 10, 25));
            assertThat(event.confirmedBy()).isEqualTo(confirmedBy);
            assertThat(event.lines()).containsExactly(line);

            assertThatThrownBy(() -> event.lines().add(line))
                    .isInstanceOf(UnsupportedOperationException.class);

            assertThatThrownBy(() -> new ReservationConfirmed(null, now, resId, "C", clientId, providerId, LocalDate.now(), LocalDate.now(), confirmedBy, lines))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("ReservationCancelled valida no-nulos y preserva motivo opcional")
        void reservationCancelledContract() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now();
            UUID resId = UUID.randomUUID();
            UUID cancelledBy = UUID.randomUUID();

            ReservationCancelled event = new ReservationCancelled(
                    eventId,
                    now,
                    resId,
                    "PENDING",
                    cancelledBy,
                    "Cliente desistio del alquiler"
            );

            assertThat(event.eventId()).isEqualTo(eventId);
            assertThat(event.occurredAt()).isEqualTo(now);
            assertThat(event.reservationId()).isEqualTo(resId);
            assertThat(event.previousStatus()).isEqualTo("PENDING");
            assertThat(event.cancelledBy()).isEqualTo(cancelledBy);
            assertThat(event.reason()).isEqualTo("Cliente desistio del alquiler");

            assertThatThrownBy(() -> new ReservationCancelled(null, now, resId, "PENDING", cancelledBy, "r"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("ReservationReturned valida no-nulos y lista inmutable de unidades devueltas")
        void reservationReturnedContract() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now();
            UUID resId = UUID.randomUUID();
            UUID registeredBy = UUID.randomUUID();

            ReservationReturned.ReturnedUnit unit = new ReservationReturned.ReturnedUnit(
                    UUID.randomUUID(),
                    "Generador 5.5 KVA",
                    LocalDate.of(2026, 10, 22),
                    2,
                    new BigDecimal("60.00"),
                    true,
                    false
            );

            ReservationReturned event = new ReservationReturned(
                    eventId,
                    now,
                    resId,
                    registeredBy,
                    List.of(unit)
            );

            assertThat(event.eventId()).isEqualTo(eventId);
            assertThat(event.occurredAt()).isEqualTo(now);
            assertThat(event.reservationId()).isEqualTo(resId);
            assertThat(event.registeredBy()).isEqualTo(registeredBy);
            assertThat(event.units()).containsExactly(unit);
            assertThat(unit.lateDays()).isEqualTo(2);
            assertThat(unit.damageReported()).isTrue();
            assertThat(unit.lost()).isFalse();

            assertThatThrownBy(() -> event.units().add(unit))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("rental.api: Directorio y Participantes")
    class RentalApiTests {

        @Test
        @DisplayName("ReservationParticipants valida todos los campos obligatorios")
        void reservationParticipantsContract() {
            UUID resId = UUID.randomUUID();
            UUID clientId = UUID.randomUUID();
            UUID providerId = UUID.randomUUID();

            ReservationParticipants participants = new ReservationParticipants(
                    resId,
                    "RES-2026-0099",
                    clientId,
                    providerId
            );

            assertThat(participants.reservationId()).isEqualTo(resId);
            assertThat(participants.code()).isEqualTo("RES-2026-0099");
            assertThat(participants.clientId()).isEqualTo(clientId);
            assertThat(participants.providerId()).isEqualTo(providerId);

            assertThatThrownBy(() -> new ReservationParticipants(null, "C", clientId, providerId))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("ReservationDirectory puede implementarse como interfaz funcional / lambda")
        void reservationDirectoryInterface() {
            UUID resId = UUID.randomUUID();
            ReservationParticipants expected = new ReservationParticipants(resId, "RES-01", UUID.randomUUID(), UUID.randomUUID());

            ReservationDirectory directory = id -> id.equals(resId) ? Optional.of(expected) : Optional.empty();

            assertThat(directory.findParticipants(resId)).contains(expected);
            assertThat(directory.findParticipants(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("inventory.api: Tarifas, Catalogo y Gestion de Unidades")
    class InventoryApiTests {

        @Test
        @DisplayName("RatePeriod contiene exactamente DAY, WEEK y MONTH")
        void ratePeriodEnumValues() {
            assertThat(RatePeriod.values()).containsExactly(
                    RatePeriod.DAY,
                    RatePeriod.WEEK,
                    RatePeriod.MONTH
            );
        }

        @Test
        @DisplayName("ToolRentalInfo valida inmutabilidad de rates y campos requeridos")
        void toolRentalInfoContract() {
            UUID toolId = UUID.randomUUID();
            UUID providerId = UUID.randomUUID();
            Map<RatePeriod, BigDecimal> mutableRates = new HashMap<>();
            mutableRates.put(RatePeriod.DAY, new BigDecimal("45.00"));
            mutableRates.put(RatePeriod.WEEK, new BigDecimal("250.00"));
            mutableRates.put(RatePeriod.MONTH, new BigDecimal("850.00"));

            ToolRentalInfo info = new ToolRentalInfo(
                    toolId,
                    providerId,
                    "Amoladora Angular 9 pulg",
                    true,
                    new BigDecimal("150.00"),
                    mutableRates
            );

            assertThat(info.toolId()).isEqualTo(toolId);
            assertThat(info.providerId()).isEqualTo(providerId);
            assertThat(info.name()).isEqualTo("Amoladora Angular 9 pulg");
            assertThat(info.active()).isTrue();
            assertThat(info.depositAmount()).isEqualByComparingTo("150.00");
            assertThat(info.rates()).containsEntry(RatePeriod.DAY, new BigDecimal("45.00"));

            mutableRates.put(RatePeriod.DAY, new BigDecimal("999.00"));
            assertThat(info.rates().get(RatePeriod.DAY)).isEqualByComparingTo("45.00");

            assertThatThrownBy(() -> info.rates().put(RatePeriod.DAY, BigDecimal.TEN))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("UnitRef valida identificadores y numero de serie")
        void unitRefContract() {
            UUID unitId = UUID.randomUUID();
            UUID toolId = UUID.randomUUID();

            UnitRef ref = new UnitRef(unitId, toolId, "SN-AMO-9942");

            assertThat(ref.unitId()).isEqualTo(unitId);
            assertThat(ref.toolId()).isEqualTo(toolId);
            assertThat(ref.serialNumber()).isEqualTo("SN-AMO-9942");

            assertThatThrownBy(() -> new UnitRef(null, toolId, "SN"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("UnitInventoryApi y ToolCatalogApi pueden ser implementados en fakes de prueba")
        void unitInventoryApiAndCatalogContracts() {
            UUID toolId = UUID.randomUUID();
            UUID unitId = UUID.randomUUID();
            UnitRef unitRef = new UnitRef(unitId, toolId, "SN-001");

            UnitInventoryApi fakeInventory = new UnitInventoryApi() {
                @Override
                public List<UnitRef> lockOperationalUnits(UUID tId) {
                    return List.of(unitRef);
                }

                @Override
                public Optional<UnitRef> findUnit(UUID uId) {
                    return uId.equals(unitId) ? Optional.of(unitRef) : Optional.empty();
                }

                @Override
                public void sendToMaintenance(UUID uId, String reason) {}

                @Override
                public void retire(UUID uId, String reason) {}
            };

            assertThat(fakeInventory.lockOperationalUnits(toolId)).containsExactly(unitRef);
            assertThat(fakeInventory.findUnit(unitId)).contains(unitRef);
            assertThat(fakeInventory.findUnit(UUID.randomUUID())).isEmpty();

            ToolRentalInfo toolInfo = new ToolRentalInfo(
                    toolId,
                    UUID.randomUUID(),
                    "Taladro Percutor",
                    true,
                    new BigDecimal("100.00"),
                    Map.of(RatePeriod.DAY, new BigDecimal("30.00"))
            );

            ToolCatalogApi fakeCatalog = tId -> tId.equals(toolId) ? Optional.of(toolInfo) : Optional.empty();
            assertThat(fakeCatalog.findRentableTool(toolId)).contains(toolInfo);
            assertThat(fakeCatalog.findRentableTool(UUID.randomUUID())).isEmpty();
        }
    }
}
