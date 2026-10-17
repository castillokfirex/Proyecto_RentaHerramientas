package com.rentatools.rental.application.mapper;

import com.rentatools.rental.application.dto.ChecklistResultView;
import com.rentatools.rental.application.dto.InspectionPhotoView;
import com.rentatools.rental.application.dto.InspectionView;
import com.rentatools.rental.domain.model.checklist.ChecklistItem;
import com.rentatools.rental.domain.model.inspection.ChecklistResult;
import com.rentatools.rental.domain.model.inspection.Inspection;
import com.rentatools.rental.domain.model.inspection.InspectionPhoto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mapper para traducir agregados {@link Inspection} a vistas {@link InspectionView}.
 */
public final class InspectionViewMapper {

    private InspectionViewMapper() {
    }

    public static InspectionView toView(Inspection inspection, Map<Integer, ChecklistItem> checklistCatalog) {
        Objects.requireNonNull(inspection, "inspection must not be null");

        List<ChecklistResultView> resultViews = new ArrayList<>(inspection.results().size());
        for (ChecklistResult r : inspection.results()) {
            int itemId = r.checklistItemId().value();
            ChecklistItem item = checklistCatalog != null ? checklistCatalog.get(itemId) : null;
            resultViews.add(new ChecklistResultView(
                    itemId,
                    item != null ? item.code() : null,
                    item != null ? item.label() : null,
                    r.result(),
                    r.note()
            ));
        }

        List<InspectionPhotoView> photoViews = new ArrayList<>(inspection.photos().size());
        for (InspectionPhoto p : inspection.photos()) {
            photoViews.add(new InspectionPhotoView(
                    p.id(),
                    inspection.id().value(),
                    p.storageKey(),
                    p.contentType(),
                    p.sizeBytes(),
                    p.uploadedAt()
            ));
        }

        return new InspectionView(
                inspection.id().value(),
                inspection.reservationUnitId().value(),
                inspection.type(),
                inspection.inspectorId(),
                inspection.performedAt(),
                inspection.overallCondition(),
                inspection.observations(),
                resultViews,
                photoViews
        );
    }
}
