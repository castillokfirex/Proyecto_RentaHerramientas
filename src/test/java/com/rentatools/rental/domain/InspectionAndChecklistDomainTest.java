package com.rentatools.rental.domain;

import com.rentatools.rental.domain.exception.DeliveryNotRegisteredException;
import com.rentatools.rental.domain.exception.InspectionAlreadyRegisteredException;
import com.rentatools.rental.domain.model.checklist.ChecklistItem;
import com.rentatools.rental.domain.model.checklist.ChecklistItemId;
import com.rentatools.rental.domain.model.inspection.ChecklistResult;
import com.rentatools.rental.domain.model.inspection.Inspection;
import com.rentatools.rental.domain.model.inspection.InspectionType;
import com.rentatools.rental.domain.model.inspection.OverallCondition;
import com.rentatools.rental.domain.model.inspection.ResultValue;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionAndChecklistDomainTest {

    private static final Clock DELIVERY_CLOCK = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC);
    private static final Clock RETURN_CLOCK = Clock.fixed(Instant.parse("2026-10-21T16:00:00Z"), ZoneOffset.UTC);

    private static final ReservationUnitId RES_UNIT_ID = ReservationUnitId.of(Ids.newId());
    private static final UUID INSPECTOR_ID = Ids.newId();
    private static final ChecklistItemId ITEM_CARCASA = ChecklistItemId.of(1);
    private static final ChecklistItemId ITEM_CABLE = ChecklistItemId.of(2);

    @Test
    void shouldCreateAndUpdateChecklistItemNormalized() {
        ChecklistItem item = ChecklistItem.create(ChecklistItemId.of(1), " carcasa ", " Carcasa sin fisuras ");
        assertThat(item.code()).isEqualTo("CARCASA");
        assertThat(item.label()).isEqualTo("Carcasa sin fisuras");
        assertThat(item.isActive()).isTrue();

        item.update("Carcasa actualizada", false);
        assertThat(item.label()).isEqualTo("Carcasa actualizada");
        assertThat(item.isActive()).isFalse();
    }

    @Test
    void shouldRejectReturnInspectionWhenDeliveryNotRegistered() {
        List<ChecklistResult> results = List.of(
                ChecklistResult.of(ITEM_CARCASA, ResultValue.OK)
        );

        assertThatThrownBy(() -> Inspection.create(
                RES_UNIT_ID,
                InspectionType.RETURN,
                INSPECTOR_ID,
                OverallCondition.GOOD,
                "Devolución sin entrega previa",
                results,
                List.of(),
                RETURN_CLOCK
        ))
                .isInstanceOf(DeliveryNotRegisteredException.class)
                .extracting("code")
                .isEqualTo("DELIVERY_NOT_REGISTERED");

        assertThatThrownBy(() -> Inspection.createReturn(
                RES_UNIT_ID,
                INSPECTOR_ID,
                OverallCondition.GOOD,
                "Devolución sin entrega previa",
                results,
                null,
                RETURN_CLOCK
        ))
                .isInstanceOf(DeliveryNotRegisteredException.class);
    }

    @Test
    void shouldRejectDuplicateInspectionOfSameTypeForSameUnit() {
        List<ChecklistResult> results = List.of(ChecklistResult.of(ITEM_CARCASA, ResultValue.OK));
        Inspection delivery = Inspection.createDelivery(
                RES_UNIT_ID,
                INSPECTOR_ID,
                OverallCondition.GOOD,
                "Entrega inicial",
                results,
                DELIVERY_CLOCK
        );

        assertThatThrownBy(() -> Inspection.create(
                RES_UNIT_ID,
                InspectionType.DELIVERY,
                INSPECTOR_ID,
                OverallCondition.GOOD,
                "Segunda entrega duplicada",
                results,
                List.of(delivery),
                RETURN_CLOCK
        ))
                .isInstanceOf(InspectionAlreadyRegisteredException.class)
                .extracting("code")
                .isEqualTo("INSPECTION_ALREADY_REGISTERED");
    }

    @Test
    void shouldRejectGoodOverallConditionWhenChecklistContainsDamagedOrMissingItems() {
        List<ChecklistResult> damagedResults = List.of(
                ChecklistResult.of(ITEM_CARCASA, ResultValue.OK),
                ChecklistResult.of(ITEM_CABLE, ResultValue.DAMAGED, "Cable pelado")
        );

        assertThatThrownBy(() -> Inspection.createDelivery(
                RES_UNIT_ID,
                INSPECTOR_ID,
                OverallCondition.GOOD,
                "Incoherente",
                damagedResults,
                DELIVERY_CLOCK
        ))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INCONSISTENT_OVERALL_CONDITION");

        List<ChecklistResult> missingResults = List.of(
                ChecklistResult.of(ITEM_CARCASA, ResultValue.MISSING, "Falta maletín")
        );

        assertThatThrownBy(() -> Inspection.createDelivery(
                RES_UNIT_ID,
                INSPECTOR_ID,
                OverallCondition.GOOD,
                "Incoherente",
                missingResults,
                DELIVERY_CLOCK
        ))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("INCONSISTENT_OVERALL_CONDITION");

        // Pero sí permite MINOR_DAMAGE o MAJOR_DAMAGE
        Inspection validMinorDamage = Inspection.createDelivery(
                RES_UNIT_ID,
                INSPECTOR_ID,
                OverallCondition.MINOR_DAMAGE,
                "Se entrega con raspón leve",
                damagedResults,
                DELIVERY_CLOCK
        );
        assertThat(validMinorDamage.overallCondition()).isEqualTo(OverallCondition.MINOR_DAMAGE);
    }

    @Test
    void shouldAllowLostConditionOnlyOnReturnInspection() {
        List<ChecklistResult> results = List.of(
                ChecklistResult.of(ITEM_CARCASA, ResultValue.MISSING, "Extraviada")
        );

        assertThatThrownBy(() -> Inspection.createDelivery(
                RES_UNIT_ID,
                INSPECTOR_ID,
                OverallCondition.LOST,
                "No puede perderse en la entrega",
                results,
                DELIVERY_CLOCK
        ))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("LOST_NOT_ALLOWED_ON_DELIVERY");

        Inspection delivery = Inspection.createDelivery(
                RES_UNIT_ID,
                INSPECTOR_ID,
                OverallCondition.GOOD,
                "Entregada OK",
                List.of(ChecklistResult.of(ITEM_CARCASA, ResultValue.OK)),
                DELIVERY_CLOCK
        );

        Inspection returnLost = Inspection.createReturn(
                RES_UNIT_ID,
                INSPECTOR_ID,
                OverallCondition.LOST,
                "Cliente reporta extravío total",
                results,
                delivery,
                RETURN_CLOCK
        );
        assertThat(returnLost.overallCondition()).isEqualTo(OverallCondition.LOST);
        assertThat(returnLost.type()).isEqualTo(InspectionType.RETURN);
    }

    @Test
    void shouldEnforceMaximumOf10PhotosPerInspection() {
        Inspection delivery = Inspection.createDelivery(
                RES_UNIT_ID,
                INSPECTOR_ID,
                OverallCondition.GOOD,
                "Entrega con evidencia fotográfica",
                List.of(ChecklistResult.of(ITEM_CARCASA, ResultValue.OK)),
                DELIVERY_CLOCK
        );

        for (int i = 1; i <= 10; i++) {
            delivery.attachPhoto("inspections/photo-" + i + ".jpg", "image/jpeg", 102_400L, DELIVERY_CLOCK);
        }
        assertThat(delivery.photos()).hasSize(10);

        assertThatThrownBy(() -> delivery.attachPhoto(
                "inspections/photo-11.jpg",
                "image/jpeg",
                102_400L,
                DELIVERY_CLOCK
        ))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting("code")
                .isEqualTo("PHOTO_LIMIT_EXCEEDED");
    }
}
