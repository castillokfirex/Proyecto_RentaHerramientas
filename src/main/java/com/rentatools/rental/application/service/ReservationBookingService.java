package com.rentatools.rental.application.service;

import com.rentatools.rental.application.dto.CreateReservationCommand;
import com.rentatools.rental.application.dto.QuoteReservationCommand;
import com.rentatools.rental.application.dto.RequestedToolItem;
import com.rentatools.rental.application.dto.ReservationView;
import com.rentatools.rental.application.mapper.ReservationViewMapper;
import com.rentatools.rental.application.port.in.CreateReservationUseCase;
import com.rentatools.rental.application.port.in.QuoteReservationUseCase;
import com.rentatools.rental.application.port.out.ReservationRepository;
import com.rentatools.rental.application.port.out.ToolCatalogPort;
import com.rentatools.rental.application.port.out.ToolCatalogPort.RentableToolDescriptor;
import com.rentatools.rental.application.port.out.UnitAvailabilityPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort;
import com.rentatools.rental.application.port.out.UnitInventoryPort.OperationalUnitDescriptor;
import com.rentatools.rental.application.port.out.UserVerificationPort;
import com.rentatools.rental.domain.model.pricing.Quote;
import com.rentatools.rental.domain.model.pricing.QuoteLine;
import com.rentatools.rental.domain.model.reservation.ClientId;
import com.rentatools.rental.domain.model.reservation.ProviderId;
import com.rentatools.rental.domain.model.reservation.Reservation;
import com.rentatools.rental.domain.model.reservation.ReservationCode;
import com.rentatools.rental.domain.model.reservation.ReservationId;
import com.rentatools.rental.domain.model.reservation.ReservationUnit;
import com.rentatools.rental.domain.model.reservation.ReservationUnitId;
import com.rentatools.rental.domain.model.reservation.UnitId;
import com.rentatools.rental.domain.service.PricingPolicy;
import com.rentatools.rental.domain.service.UnitAllocator;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.Actor;
import com.rentatools.shared.domain.ActorRole;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.Money;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Servicio de aplicación que implementa {@link QuoteReservationUseCase} y {@link CreateReservationUseCase}.
 *
 * <p>Al crear una reserva, bloquea de forma determinista las unidades operativas de cada herramienta
 * con {@code SELECT ... FOR UPDATE}, filtra las unidades ocupadas por solapamiento o devolución demorada,
 * asigna las primeras disponibles con {@link UnitAllocator} y congela las tarifas pactadas.</p>
 */
public class ReservationBookingService implements QuoteReservationUseCase, CreateReservationUseCase {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final ReservationRepository reservationRepository;
    private final UnitAvailabilityPort unitAvailabilityPort;
    private final ToolCatalogPort toolCatalogPort;
    private final UnitInventoryPort unitInventoryPort;
    private final UserVerificationPort userVerificationPort;
    private final PricingPolicy pricingPolicy;
    private final UnitAllocator unitAllocator;
    private final Clock clock;

    public ReservationBookingService(
            ReservationRepository reservationRepository,
            UnitAvailabilityPort unitAvailabilityPort,
            ToolCatalogPort toolCatalogPort,
            UnitInventoryPort unitInventoryPort,
            UserVerificationPort userVerificationPort,
            PricingPolicy pricingPolicy,
            UnitAllocator unitAllocator,
            Clock clock
    ) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository must not be null");
        this.unitAvailabilityPort = Objects.requireNonNull(unitAvailabilityPort, "unitAvailabilityPort must not be null");
        this.toolCatalogPort = Objects.requireNonNull(toolCatalogPort, "toolCatalogPort must not be null");
        this.unitInventoryPort = Objects.requireNonNull(unitInventoryPort, "unitInventoryPort must not be null");
        this.userVerificationPort = Objects.requireNonNull(userVerificationPort, "userVerificationPort must not be null");
        this.pricingPolicy = Objects.requireNonNull(pricingPolicy, "pricingPolicy must not be null");
        this.unitAllocator = Objects.requireNonNull(unitAllocator, "unitAllocator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Quote quoteReservation(Actor actor, QuoteReservationCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        assertClientActor(actor);

        Map<UUID, Integer> quantitiesByTool = consolidateRequestedItems(command.items());
        Set<ProviderId> providers = new LinkedHashSet<>();
        List<QuoteLine> quoteLines = new ArrayList<>(quantitiesByTool.size());

        for (Map.Entry<UUID, Integer> entry : quantitiesByTool.entrySet()) {
            RentableToolDescriptor tool = loadActiveToolOrThrow(entry.getKey());
            providers.add(ProviderId.of(tool.providerId()));
            quoteLines.add(pricingPolicy.quoteLine(
                    tool.toolId(),
                    entry.getValue(),
                    tool.rateCard(),
                    command.dateRange()
            ));
        }

        Reservation.ensureSingleProvider(providers);
        return Quote.of(command.dateRange(), quoteLines);
    }

    @Override
    public ReservationView createReservation(Actor actor, CreateReservationCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(command, "command must not be null");
        assertClientActor(actor);
        if (!userVerificationPort.isActiveClient(actor.userId())) {
            throw new ForbiddenException("CLIENT_NOT_ACTIVE", "User is not an active client: " + actor.userId());
        }

        // Ordenar por toolId evita interbloqueos cuando dos transacciones reservan varias herramientas a la vez
        Map<UUID, Integer> orderedQuantitiesByTool = new TreeMap<>(consolidateRequestedItems(command.items()));
        Map<UUID, RentableToolDescriptor> toolsById = new LinkedHashMap<>();
        Set<ProviderId> providers = new LinkedHashSet<>();

        for (UUID toolId : orderedQuantitiesByTool.keySet()) {
            RentableToolDescriptor tool = loadActiveToolOrThrow(toolId);
            toolsById.put(toolId, tool);
            providers.add(ProviderId.of(tool.providerId()));
        }

        ProviderId singleProvider = Reservation.ensureSingleProvider(providers);

        List<ReservationUnit> reservationUnits = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : orderedQuantitiesByTool.entrySet()) {
            UUID toolId = entry.getKey();
            int requestedQty = entry.getValue();
            RentableToolDescriptor tool = toolsById.get(toolId);

            // 1. SELECT ... FOR UPDATE sobre las unidades AVAILABLE de la herramienta
            List<OperationalUnitDescriptor> lockedUnits = unitInventoryPort.lockOperationalUnits(toolId);
            List<UnitId> operationalUnitIds = new ArrayList<>(lockedUnits.size());
            for (OperationalUnitDescriptor u : lockedUnits) {
                operationalUnitIds.add(UnitId.of(u.unitId()));
            }

            // 2. Consultar cuáles de las unidades candidatas están ocupadas por solapamiento o devolución tardía
            Set<UnitId> occupiedUnitIds = operationalUnitIds.isEmpty()
                    ? Set.of()
                    : unitAvailabilityPort.findOccupiedUnits(operationalUnitIds, command.dateRange());

            // 3. Asignar las primeras unidades disponibles o lanzar InsufficientAvailabilityException
            List<UnitId> allocatedUnitIds = unitAllocator.allocate(
                    toolId,
                    operationalUnitIds,
                    occupiedUnitIds,
                    requestedQty
            );

            // 4. Congelar tarifas históricas en cada ReservationUnit
            Money dailyRate = tool.rateCard().dailyRate();
            Money unitAgreedAmount = pricingPolicy.calculateAgreedAmount(tool.rateCard(), command.dateRange());
            Money unitDepositAmount = tool.rateCard().depositAmount();

            for (UnitId allocatedUnitId : allocatedUnitIds) {
                reservationUnits.add(ReservationUnit.create(
                        ReservationUnitId.generate(clock),
                        allocatedUnitId,
                        singleProvider,
                        dailyRate,
                        unitAgreedAmount,
                        unitDepositAmount
                ));
            }
        }

        ReservationCode uniqueCode = generateUniqueCode();
        Reservation reservation = Reservation.create(
                ReservationId.generate(clock),
                uniqueCode,
                ClientId.of(actor.userId()),
                singleProvider,
                command.dateRange(),
                reservationUnits,
                clock
        );

        Reservation saved = reservationRepository.save(reservation);
        return ReservationViewMapper.toView(saved, unitInventoryPort, toolCatalogPort);
    }

    private RentableToolDescriptor loadActiveToolOrThrow(UUID toolId) {
        RentableToolDescriptor tool = toolCatalogPort.findRentableTool(toolId)
                .orElseThrow(() -> new NotFoundException("TOOL_NOT_FOUND", "Tool not found: " + toolId));
        if (!tool.active()) {
            throw new BusinessRuleViolation("TOOL_NOT_ACTIVE", "Tool is not active for rental: " + toolId);
        }
        return tool;
    }

    private ReservationCode generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            ReservationCode candidate = ReservationCode.generate();
            if (!reservationRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleViolation(
                "RESERVATION_CODE_GENERATION_FAILED",
                "Could not generate a unique reservation code after " + MAX_CODE_GENERATION_ATTEMPTS + " attempts"
        );
    }

    private static Map<UUID, Integer> consolidateRequestedItems(List<RequestedToolItem> items) {
        Map<UUID, Integer> map = new LinkedHashMap<>();
        for (RequestedToolItem item : items) {
            map.merge(item.toolId(), item.quantity(), Integer::sum);
        }
        return map;
    }

    private static void assertClientActor(Actor actor) {
        if (!actor.is(ActorRole.CLIENTE) && !actor.is(ActorRole.ADMIN)) {
            throw new ForbiddenException("CLIENT_ROLE_REQUIRED", "Only CLIENTE can quote or create reservations");
        }
    }
}
