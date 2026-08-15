package com.eventplatform.event.application;

import com.eventplatform.event.api.EventApi;
import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.api.RequestContext;
import com.eventplatform.event.domain.CategoryStatus;
import com.eventplatform.event.domain.EventCategory;
import com.eventplatform.event.domain.EventStatus;
import com.eventplatform.event.domain.ManagedEvent;
import com.eventplatform.event.domain.ManagedEventRepository;
import com.eventplatform.event.domain.TicketType;
import com.eventplatform.event.domain.TicketTypeRepository;
import com.eventplatform.event.domain.TicketTypeStatus;
import com.eventplatform.event.integration.EventLifecycleEvents;
import com.eventplatform.event.outbox.TransactionalOutbox;
import com.eventplatform.event.security.AuthenticatedActor;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventManagementService {

    private final ManagedEventRepository eventRepository;
    private final TicketTypeRepository ticketRepository;
    private final EventCategoryService categoryService;
    private final VenueAvailabilityPort venueAvailabilityPort;
    private final EventResponseMapper mapper;
    private final TransactionalOutbox outbox;
    private final PublicEventCache cache;

    public EventManagementService(
            ManagedEventRepository eventRepository,
            TicketTypeRepository ticketRepository,
            EventCategoryService categoryService,
            VenueAvailabilityPort venueAvailabilityPort,
            EventResponseMapper mapper,
            TransactionalOutbox outbox,
            PublicEventCache cache) {
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.categoryService = categoryService;
        this.venueAvailabilityPort = venueAvailabilityPort;
        this.mapper = mapper;
        this.outbox = outbox;
        this.cache = cache;
    }

    @Transactional
    public EventApi.EventResponse create(
            EventApi.EventRequest request,
            AuthenticatedActor actor,
            RequestContext context) {
        categoryService.requiredActive(request.categoryId());
        validateSchedule(request.startsAt(), request.endsAt(), false);
        Instant now = Instant.now();
        ManagedEvent event = new ManagedEvent(
                UUID.randomUUID(),
                actor.userId(),
                request.title().trim(),
                request.description().trim(),
                request.categoryId(),
                validTimezone(request.timezone()),
                request.startsAt(),
                request.endsAt(),
                request.venueId(),
                request.venueSpaceId(),
                request.capacity(),
                now);
        eventRepository.save(event);
        appendUpdated(event, context, now);
        return mapper.management(event);
    }

    @Transactional(readOnly = true)
    public EventApi.EventResponse getManagement(UUID eventId, AuthenticatedActor actor) {
        ManagedEvent event = requiredEvent(eventId);
        requireOwner(event, actor);
        return mapper.management(event);
    }

    @Transactional
    public EventApi.EventResponse update(
            UUID eventId,
            EventApi.EventRequest request,
            AuthenticatedActor actor,
            RequestContext context) {
        ManagedEvent event = requiredEventForUpdate(eventId);
        requireOwner(event, actor);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_DETAILS_LOCKED",
                    "Schedule, venue, category, and capacity can change only while an event is a draft");
        }
        categoryService.requiredActive(request.categoryId());
        validateSchedule(request.startsAt(), request.endsAt(), false);
        long allocated = ticketRepository.findAllByEventIdAndStatusInOrderByPriceAscNameAsc(
                        eventId, EnumSet.of(TicketTypeStatus.ACTIVE, TicketTypeStatus.PAUSED)).stream()
                .mapToLong(TicketType::getAllocation)
                .sum();
        if (allocated > request.capacity()) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_CAPACITY_BELOW_TICKET_ALLOCATION",
                    "Event capacity cannot be lower than current ticket allocation");
        }
        validateTicketWindowsForSchedule(eventId, request.startsAt());
        Instant now = Instant.now();
        event.update(
                request.title().trim(),
                request.description().trim(),
                request.categoryId(),
                validTimezone(request.timezone()),
                request.startsAt(),
                request.endsAt(),
                request.venueId(),
                request.venueSpaceId(),
                request.capacity(),
                now);
        appendUpdated(event, context, now);
        cache.evict(eventId);
        return mapper.management(event);
    }

    @Transactional
    public EventApi.EventResponse transition(
            UUID eventId,
            EventStatus target,
            AuthenticatedActor actor,
            RequestContext context,
            String bearerToken) {
        ManagedEvent event = requiredEventForUpdate(eventId);
        requireOwner(event, actor);
        if (event.getStatus() == target) {
            return mapper.management(event);
        }
        Instant now = Instant.now();
        EventStatus previous = event.getStatus();
        switch (target) {
            case PUBLISHED -> publish(event, context, bearerToken, now);
            case SALES_OPEN -> openSales(event, now);
            case SOLD_OUT -> markSoldOut(event, now);
            case CANCELLED -> cancel(event, bearerToken, now);
            case COMPLETED -> complete(event, now);
            default -> throw invalidTransition(previous, target);
        }
        if (target == EventStatus.CANCELLED) {
            outbox.append(
                    "Event",
                    event.getId(),
                    EventLifecycleEvents.EVENT_CANCELLED,
                    EventLifecycleEvents.VERSION,
                    new EventLifecycleEvents.EventCancelledV1(event.getId(), event.getOrganizerId(), previous, now),
                    context,
                    now);
        } else if (target != EventStatus.PUBLISHED) {
            appendUpdated(event, context, now);
        }
        cache.evict(eventId);
        return mapper.management(event);
    }

    @Transactional
    public void archive(
            UUID eventId,
            AuthenticatedActor actor,
            RequestContext context) {
        ManagedEvent event = requiredEventForUpdate(eventId);
        requireOwner(event, actor);
        if (event.getStatus() == EventStatus.ARCHIVED) {
            return;
        }
        if (!EnumSet.of(EventStatus.DRAFT, EventStatus.CANCELLED, EventStatus.COMPLETED)
                .contains(event.getStatus())) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_CANNOT_BE_ARCHIVED",
                    "Only draft, cancelled, or completed events can be archived");
        }
        Instant now = Instant.now();
        event.transitionTo(EventStatus.ARCHIVED, now);
        appendUpdated(event, context, now);
        cache.evict(eventId);
    }

    ManagedEvent requiredEvent(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new EventApiException(
                HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "The event was not found"));
    }

    ManagedEvent requiredEventForUpdate(UUID eventId) {
        return eventRepository.findByIdForUpdate(eventId).orElseThrow(() -> new EventApiException(
                HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "The event was not found"));
    }

    void requireOwner(ManagedEvent event, AuthenticatedActor actor) {
        if (!actor.isAdmin() && !actor.owns(event.getOrganizerId())) {
            throw new EventApiException(HttpStatus.FORBIDDEN, "EVENT_OWNERSHIP_REQUIRED", "Event ownership is required");
        }
    }

    private void publish(ManagedEvent event, RequestContext context, String bearerToken, Instant now) {
        if (event.getStatus() != EventStatus.DRAFT) {
            throw invalidTransition(event.getStatus(), EventStatus.PUBLISHED);
        }
        validateSchedule(event.getStartsAt(), event.getEndsAt(), true);
        EventCategory category = categoryService.requiredActive(event.getCategoryId());
        if (category.getStatus() != CategoryStatus.ACTIVE) {
            throw new EventApiException(HttpStatus.CONFLICT, "CATEGORY_ARCHIVED", "The event category is archived");
        }
        List<TicketType> tickets = ticketRepository.findAllByEventIdAndStatusInOrderByPriceAscNameAsc(
                event.getId(), EnumSet.of(TicketTypeStatus.ACTIVE));
        validatePublicationTickets(event, tickets);
        VenueAvailabilityPort.Reservation reservation = venueAvailabilityPort.reserve(
                event.getVenueId(),
                event.getVenueSpaceId(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCapacity(),
                "event:" + event.getId(),
                requireBearer(bearerToken));
        event.publish(reservation.id(), now);
        outbox.append(
                "Event",
                event.getId(),
                EventLifecycleEvents.EVENT_PUBLISHED,
                EventLifecycleEvents.VERSION,
                new EventLifecycleEvents.EventPublishedV1(
                        event.getId(),
                        event.getOrganizerId(),
                        event.getCategoryId(),
                        event.getVenueId(),
                        event.getVenueSpaceId(),
                        event.getStartsAt(),
                        event.getEndsAt(),
                        event.getCapacity(),
                        event.getStatus(),
                        now),
                context,
                now);
    }

    private void openSales(ManagedEvent event, Instant now) {
        if (!EnumSet.of(EventStatus.PUBLISHED, EventStatus.SOLD_OUT).contains(event.getStatus())) {
            throw invalidTransition(event.getStatus(), EventStatus.SALES_OPEN);
        }
        boolean onSale = ticketRepository.findAllByEventIdAndStatusInOrderByPriceAscNameAsc(
                        event.getId(), EnumSet.of(TicketTypeStatus.ACTIVE)).stream()
                .anyMatch(ticket -> ticket.isOnSale(now) && ticket.availableQuantity() > 0);
        if (!onSale) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "NO_TICKETS_ON_SALE",
                    "At least one active ticket type must be within its sales window and available");
        }
        event.transitionTo(EventStatus.SALES_OPEN, now);
    }

    private void markSoldOut(ManagedEvent event, Instant now) {
        if (event.getStatus() != EventStatus.SALES_OPEN) {
            throw invalidTransition(event.getStatus(), EventStatus.SOLD_OUT);
        }
        boolean inventoryRemaining = ticketRepository.findAllByEventIdAndStatusInOrderByPriceAscNameAsc(
                        event.getId(), EnumSet.of(TicketTypeStatus.ACTIVE)).stream()
                .anyMatch(ticket -> ticket.availableQuantity() > 0);
        if (inventoryRemaining) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "TICKET_INVENTORY_REMAINS",
                    "The event cannot be sold out while active ticket inventory remains");
        }
        event.transitionTo(EventStatus.SOLD_OUT, now);
    }

    private void cancel(ManagedEvent event, String bearerToken, Instant now) {
        if (!EnumSet.of(EventStatus.PUBLISHED, EventStatus.SALES_OPEN, EventStatus.SOLD_OUT)
                .contains(event.getStatus())) {
            throw invalidTransition(event.getStatus(), EventStatus.CANCELLED);
        }
        if (event.getVenueReservationId() != null) {
            venueAvailabilityPort.release(
                    event.getVenueId(), event.getVenueReservationId(), requireBearer(bearerToken));
        }
        event.transitionTo(EventStatus.CANCELLED, now);
    }

    private void complete(ManagedEvent event, Instant now) {
        if (!EnumSet.of(EventStatus.PUBLISHED, EventStatus.SALES_OPEN, EventStatus.SOLD_OUT)
                .contains(event.getStatus())) {
            throw invalidTransition(event.getStatus(), EventStatus.COMPLETED);
        }
        if (now.isBefore(event.getEndsAt())) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_NOT_ENDED",
                    "An event can complete only after its scheduled end");
        }
        event.transitionTo(EventStatus.COMPLETED, now);
    }

    private void validatePublicationTickets(ManagedEvent event, List<TicketType> tickets) {
        if (tickets.isEmpty()) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_REQUIRES_TICKET_TYPE",
                    "An event needs at least one active ticket type before publication");
        }
        long allocation = tickets.stream().mapToLong(TicketType::getAllocation).sum();
        if (allocation > event.getCapacity()) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "TICKET_ALLOCATION_EXCEEDS_EVENT_CAPACITY",
                    "Combined active ticket allocation exceeds event capacity");
        }
        for (TicketType ticket : tickets) {
            if (!ticket.getSalesEnd().isAfter(ticket.getSalesStart())
                    || ticket.getSalesEnd().isAfter(event.getStartsAt())) {
                throw new EventApiException(
                        HttpStatus.CONFLICT,
                        "INVALID_TICKET_SALES_WINDOW",
                        "Every active ticket type needs a valid sales window ending before the event starts");
            }
        }
    }

    private void validateTicketWindowsForSchedule(UUID eventId, Instant startsAt) {
        boolean invalid = ticketRepository.findAllByEventIdAndStatusInOrderByPriceAscNameAsc(
                        eventId, EnumSet.of(TicketTypeStatus.ACTIVE, TicketTypeStatus.PAUSED)).stream()
                .anyMatch(ticket -> ticket.getSalesEnd().isAfter(startsAt));
        if (invalid) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_START_BEFORE_TICKET_SALES_END",
                    "Update ticket sales windows before moving the event start earlier");
        }
    }

    private void validateSchedule(Instant startsAt, Instant endsAt, boolean requireFutureStart) {
        if (!endsAt.isAfter(startsAt)) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EVENT_SCHEDULE",
                    "Event end must be after event start");
        }
        if (requireFutureStart && !startsAt.isAfter(Instant.now())) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_START_NOT_FUTURE",
                    "An event must start in the future when it is published");
        }
    }

    private String validTimezone(String candidate) {
        try {
            return ZoneId.of(candidate.trim()).getId();
        } catch (ZoneRulesException exception) {
            throw new EventApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Timezone must be a valid IANA zone");
        }
    }

    private void appendUpdated(ManagedEvent event, RequestContext context, Instant now) {
        outbox.append(
                "Event",
                event.getId(),
                EventLifecycleEvents.EVENT_UPDATED,
                EventLifecycleEvents.VERSION,
                new EventLifecycleEvents.EventUpdatedV1(
                        event.getId(),
                        event.getOrganizerId(),
                        event.getCategoryId(),
                        event.getVenueId(),
                        event.getVenueSpaceId(),
                        event.getStartsAt(),
                        event.getEndsAt(),
                        event.getCapacity(),
                        event.getStatus(),
                        now),
                context,
                now);
    }

    private EventApiException invalidTransition(EventStatus current, EventStatus target) {
        return new EventApiException(
                HttpStatus.CONFLICT,
                "INVALID_EVENT_TRANSITION",
                "Event cannot transition from " + current + " to " + target);
    }

    private String requireBearer(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new EventApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "A bearer token is required for venue validation");
        }
        return bearerToken;
    }
}
