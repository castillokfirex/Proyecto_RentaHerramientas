package com.rentatools.inventory.application.port.out;

import com.rentatools.inventory.domain.model.tool.ToolId;
import com.rentatools.inventory.domain.model.unit.SerialNumber;
import com.rentatools.inventory.domain.model.unit.ToolUnit;
import com.rentatools.inventory.domain.model.unit.ToolUnitId;
import com.rentatools.inventory.domain.model.unit.UnitStatus;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia del agregado {@link ToolUnit}.
 */
public interface ToolUnitRepository {

    ToolUnit save(ToolUnit unit);

    Optional<ToolUnit> findById(ToolUnitId id);

    List<ToolUnit> findByToolId(ToolId toolId);

    boolean existsByToolIdAndSerialNumber(ToolId toolId, SerialNumber serialNumber);

    long countByToolIdAndStatus(ToolId toolId, UnitStatus status);
}
