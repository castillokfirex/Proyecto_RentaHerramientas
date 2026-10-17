package com.rentatools.rental.application.port.in;

import com.rentatools.rental.application.dto.ChecklistItemView;
import java.util.List;

/**
 * Consulta de catálogo de checklist de inspección.
 */
public interface ListChecklistItemsQuery {

    List<ChecklistItemView> listItems(boolean onlyActive);

    ChecklistItemView getItem(int itemId);
}
