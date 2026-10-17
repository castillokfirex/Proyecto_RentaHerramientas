package com.rentatools.inventory.application.port.in;

import com.rentatools.inventory.application.dto.SetToolRatesCommand;
import com.rentatools.inventory.application.dto.ToolView;
import com.rentatools.shared.domain.Actor;

/**
 * Puerto de entrada para configurar las tarifas de una herramienta.
 */
public interface SetToolRatesUseCase {

    ToolView setToolRates(Actor actor, SetToolRatesCommand command);
}
