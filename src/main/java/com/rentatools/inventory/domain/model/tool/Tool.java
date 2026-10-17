package com.rentatools.inventory.domain.model.tool;

import com.rentatools.inventory.domain.event.ToolDeactivated;
import com.rentatools.inventory.domain.event.ToolPublished;
import com.rentatools.inventory.domain.exception.ImageLimitExceededException;
import com.rentatools.inventory.domain.exception.NotToolOwnerException;
import com.rentatools.inventory.domain.exception.ToolNotPublishableException;
import com.rentatools.inventory.domain.model.category.CategoryId;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.AggregateRoot;
import com.rentatools.shared.domain.Ids;
import com.rentatools.shared.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Agregado raíz que representa una herramienta en el catálogo ({@code tools}).
 *
 * <p>Invariantes de dominio:
 * <ul>
 *   <li>{@code providerId} es inmutable y solo el proveedor dueño puede modificar la herramienta.</li>
 *   <li>Soporta tarifas por periodo ({@code DAY}, {@code WEEK}, {@code MONTH}) con monto estrictamente mayor a 0.</li>
 *   <li>Máximo 10 imágenes por herramienta; la portada es siempre la imagen en {@code position == 0}.</li>
 *   <li>Invariante de publicación: solo pasa a {@link ToolStatus#ACTIVE} si tiene tarifa {@link RatePeriod#DAY},
 *       al menos una unidad física {@code AVAILABLE} y su proveedor está aprobado.</li>
 * </ul>
 */
public final class Tool extends AggregateRoot {

    public static final int MAX_IMAGES = 10;
    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_BRAND_MODEL_LENGTH = 80;

    private final ToolId id;
    private final ProviderId providerId;
    private String name;
    private String brand;
    private String model;
    private String description;
    private Money depositAmount;
    private ToolStatus status;
    private final Set<CategoryId> categoryIds;
    private final Map<RatePeriod, ToolRate> rates;
    private final List<ToolImage> images;
    private final Instant createdAt;
    private Instant updatedAt;

    private Tool(
            ToolId id,
            ProviderId providerId,
            String name,
            String brand,
            String model,
            String description,
            Money depositAmount,
            ToolStatus status,
            Collection<CategoryId> categoryIds,
            Collection<ToolRate> rates,
            Collection<ToolImage> images,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        super(version);
        this.id = Objects.requireNonNull(id, "Tool id must not be null");
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.name = normalizeAndValidateName(name);
        this.brand = normalizeOptionalShortText(brand, "brand");
        this.model = normalizeOptionalShortText(model, "model");
        this.description = normalizeOptionalDescription(description);
        this.depositAmount = validateDepositAmount(depositAmount);
        this.status = Objects.requireNonNull(status, "Tool status must not be null");
        this.categoryIds = new LinkedHashSet<>(validateCategories(categoryIds));
        this.rates = new EnumMap<>(RatePeriod.class);
        if (rates != null) {
            for (ToolRate rate : rates) {
                Objects.requireNonNull(rate, "ToolRate must not be null");
                this.rates.put(rate.period(), rate);
            }
        }
        this.images = new ArrayList<>();
        if (images != null) {
            if (images.size() > MAX_IMAGES) {
                throw new ImageLimitExceededException("Tool cannot have more than " + MAX_IMAGES + " images");
            }
            List<ToolImage> sorted = new ArrayList<>(images);
            sorted.sort(Comparator.comparingInt(ToolImage::position));
            for (int i = 0; i < sorted.size(); i++) {
                ToolImage img = Objects.requireNonNull(sorted.get(i), "ToolImage must not be null");
                img.updatePosition(i);
                this.images.add(img);
            }
        }
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Crea una nueva herramienta en estado {@link ToolStatus#DRAFT}.
     */
    public static Tool createDraft(
            ToolId id,
            ProviderId providerId,
            String name,
            String brand,
            String model,
            String description,
            Money depositAmount,
            Collection<CategoryId> categoryIds,
            Collection<ToolRate> initialRates,
            Instant now
    ) {
        return new Tool(
                id,
                providerId,
                name,
                brand,
                model,
                description,
                depositAmount,
                ToolStatus.DRAFT,
                categoryIds,
                initialRates,
                List.of(),
                now,
                now,
                0L
        );
    }

    /**
     * Rehidrata una herramienta desde la capa de persistencia.
     */
    public static Tool rehydrate(
            ToolId id,
            ProviderId providerId,
            String name,
            String brand,
            String model,
            String description,
            Money depositAmount,
            ToolStatus status,
            Collection<CategoryId> categoryIds,
            Collection<ToolRate> rates,
            Collection<ToolImage> images,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new Tool(
                id,
                providerId,
                name,
                brand,
                model,
                description,
                depositAmount,
                status,
                categoryIds,
                rates,
                images,
                createdAt,
                updatedAt,
                version
        );
    }

    /**
     * Verifica que el actor indicado sea el proveedor dueño de la herramienta.
     */
    public void assertOwnedBy(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        assertOwnedBy(ProviderId.of(actor.userId()));
    }

    /**
     * Verifica que el {@link ProviderId} indicado sea el propietario de la herramienta.
     */
    public void assertOwnedBy(ProviderId candidateProviderId) {
        Objects.requireNonNull(candidateProviderId, "candidateProviderId must not be null");
        if (!this.providerId.equals(candidateProviderId)) {
            throw new NotToolOwnerException(
                    "Provider " + candidateProviderId + " is not the owner of tool " + this.id
            );
        }
    }

    /**
     * Verifica que el actor sea el proveedor dueño o tenga rol {@code ADMIN}.
     */
    public void assertOwnedByOrAdmin(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (actor.is(ActorRole.ADMIN)) {
            return;
        }
        assertOwnedBy(actor);
    }

    /**
     * Actualiza la información descriptiva, garantía y categorías de la herramienta.
     */
    public void updateDetails(
            String newName,
            String newBrand,
            String newModel,
            String newDescription,
            Money newDepositAmount,
            Collection<CategoryId> newCategoryIds,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        this.name = normalizeAndValidateName(newName);
        this.brand = normalizeOptionalShortText(newBrand, "brand");
        this.model = normalizeOptionalShortText(newModel, "model");
        this.description = normalizeOptionalDescription(newDescription);
        this.depositAmount = validateDepositAmount(newDepositAmount);
        Set<CategoryId> validatedCategories = validateCategories(newCategoryIds);
        this.categoryIds.clear();
        this.categoryIds.addAll(validatedCategories);
        this.updatedAt = now;
    }

    /**
     * Reemplaza el conjunto de tarifas ({@code DAY}, {@code WEEK}, {@code MONTH}) de la herramienta.
     */
    public void setRates(Collection<ToolRate> newRates, Instant now) {
        Objects.requireNonNull(newRates, "newRates must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (newRates.isEmpty()) {
            throw new IllegalArgumentException("At least one ToolRate must be provided");
        }

        Map<RatePeriod, ToolRate> mapped = new EnumMap<>(RatePeriod.class);
        for (ToolRate rate : newRates) {
            Objects.requireNonNull(rate, "ToolRate must not be null");
            if (mapped.put(rate.period(), rate) != null) {
                throw new IllegalArgumentException("Duplicate ToolRate for period " + rate.period());
            }
        }

        if (this.status == ToolStatus.ACTIVE && !mapped.containsKey(RatePeriod.DAY)) {
            throw new ToolNotPublishableException("An ACTIVE tool must keep a DAY rate");
        }

        this.rates.clear();
        this.rates.putAll(mapped);
        this.updatedAt = now;
    }

    /**
     * Agrega una nueva imagen al final de la galería (máximo 10 imágenes; posición 0 es portada).
     */
    public ToolImage addImage(
            ToolImageId imageId,
            String storageKey,
            String contentType,
            long sizeBytes,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.images.size() >= MAX_IMAGES) {
            throw new ImageLimitExceededException(
                    "Tool " + this.id + " already has the maximum of " + MAX_IMAGES + " images"
            );
        }
        int nextPosition = this.images.size();
        ToolImage image = new ToolImage(imageId, nextPosition, storageKey, contentType, sizeBytes, now);
        this.images.add(image);
        this.updatedAt = now;
        return image;
    }

    /**
     * Elimina una imagen por su identificador y compacta las posiciones {@code 0..N-1}.
     */
    public ToolImage removeImage(ToolImageId imageId, Instant now) {
        Objects.requireNonNull(imageId, "imageId must not be null");
        Objects.requireNonNull(now, "now must not be null");

        int index = -1;
        for (int i = 0; i < this.images.size(); i++) {
            if (this.images.get(i).id().equals(imageId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Image " + imageId + " does not belong to tool " + this.id);
        }

        ToolImage removed = this.images.remove(index);
        for (int i = 0; i < this.images.size(); i++) {
            this.images.get(i).updatePosition(i);
        }
        this.updatedAt = now;
        return removed;
    }

    /**
     * Reordena las imágenes de la herramienta según la lista completa de IDs indicada.
     * La primera imagen de la lista queda en {@code position == 0} (portada).
     */
    public void reorderImages(List<ToolImageId> orderedImageIds, Instant now) {
        Objects.requireNonNull(orderedImageIds, "orderedImageIds must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (orderedImageIds.size() != this.images.size()) {
            throw new IllegalArgumentException(
                    "orderedImageIds size (" + orderedImageIds.size()
                            + ") must match current images count (" + this.images.size() + ")"
            );
        }

        Set<ToolImageId> uniqueIds = new HashSet<>(orderedImageIds);
        if (uniqueIds.size() != orderedImageIds.size()) {
            throw new IllegalArgumentException("orderedImageIds must not contain duplicate image IDs");
        }

        List<ToolImage> reordered = new ArrayList<>(this.images.size());
        for (int pos = 0; pos < orderedImageIds.size(); pos++) {
            ToolImageId targetId = Objects.requireNonNull(orderedImageIds.get(pos), "imageId must not be null");
            ToolImage found = findImage(targetId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Image " + targetId + " does not belong to tool " + this.id
                    ));
            found.updatePosition(pos);
            reordered.add(found);
        }

        this.images.clear();
        this.images.addAll(reordered);
        this.updatedAt = now;
    }

    /**
     * Publica la herramienta (pasa a estado {@link ToolStatus#ACTIVE}) validando la invariante de publicación:
     * tarifa {@code DAY}, al menos una unidad {@code AVAILABLE} y proveedor aprobado.
     */
    public void publish(long availableUnitsCount, boolean providerApproved, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!providerApproved) {
            throw new ToolNotPublishableException(
                    "Tool " + this.id + " cannot be published because provider " + this.providerId + " is not APPROVED"
            );
        }
        if (!this.rates.containsKey(RatePeriod.DAY)) {
            throw new ToolNotPublishableException(
                    "Tool " + this.id + " cannot be published without a DAY rate"
            );
        }
        if (availableUnitsCount <= 0) {
            throw new ToolNotPublishableException(
                    "Tool " + this.id + " cannot be published without at least one AVAILABLE unit"
            );
        }

        if (this.status == ToolStatus.ACTIVE) {
            return;
        }

        this.status = ToolStatus.ACTIVE;
        this.updatedAt = now;
        registerEvent(new ToolPublished(Ids.newId(), now, this.id, this.providerId));
    }

    /**
     * Desactiva la herramienta (pasa a estado {@link ToolStatus#INACTIVE}) y emite {@link ToolDeactivated}.
     */
    public void deactivate(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.status == ToolStatus.INACTIVE) {
            return;
        }
        this.status = ToolStatus.INACTIVE;
        this.updatedAt = now;
        registerEvent(new ToolDeactivated(Ids.newId(), now, this.id, this.providerId));
    }

    /**
     * Retorna la imagen de portada ({@code position == 0}), si existe.
     */
    public Optional<ToolImage> coverImage() {
        return this.images.isEmpty() ? Optional.empty() : Optional.of(this.images.get(0));
    }

    private Optional<ToolImage> findImage(ToolImageId imageId) {
        for (ToolImage image : this.images) {
            if (image.id().equals(imageId)) {
                return Optional.of(image);
            }
        }
        return Optional.empty();
    }

    private static String normalizeAndValidateName(String rawName) {
        Objects.requireNonNull(rawName, "Tool name must not be null");
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Tool name must not exceed " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String normalizeOptionalShortText(String raw, String fieldName) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_BRAND_MODEL_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + MAX_BRAND_MODEL_LENGTH + " characters"
            );
        }
        return trimmed;
    }

    private static String normalizeOptionalDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Money validateDepositAmount(Money depositAmount) {
        Objects.requireNonNull(depositAmount, "depositAmount must not be null");
        if (depositAmount.isNegative()) {
            throw new IllegalArgumentException("depositAmount must be >= 0, got: " + depositAmount);
        }
        return depositAmount;
    }

    private static Set<CategoryId> validateCategories(Collection<CategoryId> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Set.of();
        }
        Set<CategoryId> result = new LinkedHashSet<>();
        for (CategoryId categoryId : categoryIds) {
            result.add(Objects.requireNonNull(categoryId, "CategoryId must not be null"));
        }
        return result;
    }

    public ToolId id() {
        return id;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public String name() {
        return name;
    }

    public String brand() {
        return brand;
    }

    public String model() {
        return model;
    }

    public String description() {
        return description;
    }

    public Money depositAmount() {
        return depositAmount;
    }

    public ToolStatus status() {
        return status;
    }

    public Set<CategoryId> categoryIds() {
        return Collections.unmodifiableSet(categoryIds);
    }

    public Map<RatePeriod, ToolRate> rates() {
        return Collections.unmodifiableMap(rates);
    }

    public List<ToolImage> images() {
        return Collections.unmodifiableList(images);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
