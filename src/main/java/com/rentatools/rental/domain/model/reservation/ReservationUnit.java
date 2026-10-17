package com.rentatools.rental.domain.model.reservation;

import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;
import java.util.Objects;

/**
 * Entidad interna del agregado {@link Reservation} que representa una unidad física reservada
 * con sus tarifas históricas congeladas al momento de crear la reserva (`reservation_units`).
 */
public final class ReservationUnit {

    private final ReservationUnitId id;
    private final UnitId unitId;
    private final ProviderId providerId;
    private final Money dailyRate;
    private final Money agreedAmount;
    private final Money depositAmount;

    private ReservationUnit(
            ReservationUnitId id,
            UnitId unitId,
            ProviderId providerId,
            Money dailyRate,
            Money agreedAmount,
            Money depositAmount
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.unitId = Objects.requireNonNull(unitId, "unitId must not be null");
        this.providerId = providerId;
        this.dailyRate = Objects.requireNonNull(dailyRate, "dailyRate must not be null");
        this.agreedAmount = Objects.requireNonNull(agreedAmount, "agreedAmount must not be null");
        this.depositAmount = Objects.requireNonNull(depositAmount, "depositAmount must not be null");

        if (!dailyRate.isPositive()) {
            throw new BusinessRuleViolation("INVALID_DAILY_RATE", "dailyRate must be strictly positive (> 0)");
        }
        if (!agreedAmount.isPositive()) {
            throw new BusinessRuleViolation("INVALID_AGREED_AMOUNT", "agreedAmount must be strictly positive (> 0)");
        }
        if (depositAmount.isNegative()) {
            throw new BusinessRuleViolation("INVALID_DEPOSIT_AMOUNT", "depositAmount must be >= 0");
        }
    }

    public static ReservationUnit create(
            UnitId unitId,
            Money dailyRate,
            Money agreedAmount,
            Money depositAmount
    ) {
        return new ReservationUnit(
                ReservationUnitId.generate(),
                unitId,
                null,
                dailyRate,
                agreedAmount,
                depositAmount
        );
    }

    public static ReservationUnit create(
            UnitId unitId,
            ProviderId providerId,
            Money dailyRate,
            Money agreedAmount,
            Money depositAmount
    ) {
        return new ReservationUnit(
                ReservationUnitId.generate(),
                unitId,
                Objects.requireNonNull(providerId, "providerId must not be null"),
                dailyRate,
                agreedAmount,
                depositAmount
        );
    }

    public static ReservationUnit create(
            ReservationUnitId id,
            UnitId unitId,
            ProviderId providerId,
            Money dailyRate,
            Money agreedAmount,
            Money depositAmount
    ) {
        return new ReservationUnit(id, unitId, providerId, dailyRate, agreedAmount, depositAmount);
    }

    public static ReservationUnit reconstruct(
            ReservationUnitId id,
            UnitId unitId,
            Money dailyRate,
            Money agreedAmount,
            Money depositAmount
    ) {
        return new ReservationUnit(id, unitId, null, dailyRate, agreedAmount, depositAmount);
    }

    public ReservationUnitId id() {
        return id;
    }

    public UnitId unitId() {
        return unitId;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public Money dailyRate() {
        return dailyRate;
    }

    public Money agreedAmount() {
        return agreedAmount;
    }

    public Money depositAmount() {
        return depositAmount;
    }
}
