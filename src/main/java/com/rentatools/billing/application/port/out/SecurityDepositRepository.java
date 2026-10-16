package com.rentatools.billing.application.port.out;

import com.rentatools.billing.domain.model.deposit.DepositId;
import com.rentatools.billing.domain.model.deposit.SecurityDeposit;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia del agregado {@link SecurityDeposit}.
 */
public interface SecurityDepositRepository {

    SecurityDeposit save(SecurityDeposit deposit);

    Optional<SecurityDeposit> findById(DepositId id);

    Optional<SecurityDeposit> findByReservationId(UUID reservationId);
}
