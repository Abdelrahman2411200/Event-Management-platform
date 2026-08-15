package com.eventplatform.event.application;

import com.eventplatform.event.api.EventApi;
import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.api.RequestContext;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketTypeService {

    private final ManagedEventRepository eventRepository;
    private final TicketTypeRepository ticketRepository;
    private final EventResponseMapper mapper;
    private final TransactionalOutbox outbox;
    private final PublicEventCache cache;

    public TicketTypeService(
            ManagedEventRepository eventRepository,
            TicketTypeRepository ticketRepository,
            EventResponseMapper mapper,
            TransactionalOutbox outbox,
            PublicEventCache cache) {
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.mapper = mapper;
        this.outbox = outbox;
        this.cache = cache;
    }

    @Transactional
    public EventApi.TicketTypeResponse create(
            UUID eventId,
            EventApi.TicketTypeRequest request,
            AuthenticatedActor actor,
            RequestContext context) {
        ManagedEvent event = requiredEventForUpdate(eventId);
        requireOwner(event, actor);
        requireMutable(event);
        validate(request, event, null);
        Instant now = Instant.now();
        TicketType ticket = new TicketType(
                UUID.randomUUID(),
                eventId,
                request.name().trim(),
                cleanNullable(request.description()),
                request.price().setScale(2),
                request.currency().trim().toUpperCase(Locale.ROOT),
                request.allocation(),
                request.salesStart(),
                request.salesEnd(),
                request.minQuantity(),
                request.maxQuantity(),
                request.status(),
                now);
        ticketRepository.save(ticket);
        appendChanged(ticket, context, now);
        cache.evict(eventId);
        return mapper.ticket(ticket);
    }

    @Transactional
    public EventApi.TicketTypeResponse update(
            UUID eventId,
            UUID ticketTypeId,
            EventApi.TicketTypeRequest request,
            AuthenticatedActor actor,
            RequestContext context) {
        ManagedEvent event = requiredEventForUpdate(eventId);
        requireOwner(event, actor);
        requireMutable(event);
        TicketType ticket = requiredTicket(ticketTypeId, eventId);
        if (ticket.getStatus() == TicketTypeStatus.ARCHIVED) {
            throw new EventApiException(HttpStatus.CONFLICT, "TICKET_TYPE_ARCHIVED", "The ticket type is archived");
        }
        validate(request, event, ticket);
        if (request.allocation() < ticket.getReservedQuantity()) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "TICKET_ALLOCATION_BELOW_RESERVED",
                    "Allocation cannot be lower than currently reserved inventory");
        }
        Instant now = Instant.now();
        ticket.update(
                request.name().trim(),
                cleanNullable(request.description()),
                request.price().setScale(2),
                request.currency().trim().toUpperCase(Locale.ROOT),
                request.allocation(),
                request.salesStart(),
                request.salesEnd(),
                request.minQuantity(),
                request.maxQuantity(),
                request.status(),
                now);
        appendChanged(ticket, context, now);
        cache.evict(eventId);
        return mapper.ticket(ticket);
    }

    @Transactional
    public void archive(
            UUID eventId,
            UUID ticketTypeId,
            AuthenticatedActor actor,
            RequestContext context) {
        ManagedEvent event = requiredEventForUpdate(eventId);
        requireOwner(event, actor);
        requireMutable(event);
        TicketType ticket = requiredTicket(ticketTypeId, eventId);
        if (ticket.getStatus() == TicketTypeStatus.ARCHIVED) {
            return;
        }
        if (ticket.getReservedQuantity() > 0) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "TICKET_TYPE_HAS_RESERVATIONS",
                    "Release active inventory reservations before archiving the ticket type");
        }
        if (event.getStatus() == EventStatus.PUBLISHED) {
            long otherActive = ticketRepository.findAllByEventIdAndStatusInOrderByPriceAscNameAsc(
                            eventId, EnumSet.of(TicketTypeStatus.ACTIVE)).stream()
                    .filter(candidate -> !candidate.getId().equals(ticketTypeId))
                    .count();
            if (otherActive == 0) {
                throw new EventApiException(
                        HttpStatus.CONFLICT,
                        "EVENT_REQUIRES_TICKET_TYPE",
                        "A published event must retain at least one active ticket type");
            }
        }
        Instant now = Instant.now();
        ticket.archive(now);
        appendChanged(ticket, context, now);
        cache.evict(eventId);
    }

    private void validate(EventApi.TicketTypeRequest request, ManagedEvent event, TicketType current) {
        if (request.status() == TicketTypeStatus.ARCHIVED) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TICKET_TYPE_STATUS",
                    "Use the archive endpoint to archive a ticket type");
        }
        if (!request.salesEnd().isAfter(request.salesStart())) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TICKET_SALES_WINDOW",
                    "Ticket sales end must be after ticket sales start");
        }
        if (request.salesEnd().isAfter(event.getStartsAt())) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "TICKET_SALES_AFTER_EVENT_START",
                    "Ticket sales must end no later than the event start");
        }
        if (request.maxQuantity() < request.minQuantity()) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TICKET_QUANTITY_LIMITS",
                    "Maximum quantity must be greater than or equal to minimum quantity");
        }
        if (request.maxQuantity() > request.allocation()) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "TICKET_MAX_EXCEEDS_ALLOCATION",
                    "Maximum booking quantity cannot exceed ticket allocation");
        }
        String requestedName = request.name().trim();
        List<TicketType> existing = ticketRepository.findAllByEventIdOrderByPriceAscNameAsc(event.getId());
        if (existing.stream().anyMatch(ticket -> !ticket.getId().equals(current == null ? null : current.getId())
                && ticket.getName().equalsIgnoreCase(requestedName))) {
            throw new EventApiException(HttpStatus.CONFLICT, "TICKET_TYPE_NAME_EXISTS", "Ticket type name already exists");
        }
        if (event.getStatus() == EventStatus.PUBLISHED
                && request.status() != TicketTypeStatus.ACTIVE
                && existing.stream().noneMatch(ticket ->
                        (current == null || !ticket.getId().equals(current.getId()))
                                && ticket.getStatus() == TicketTypeStatus.ACTIVE)) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_REQUIRES_TICKET_TYPE",
                    "A published event must retain at least one active ticket type");
        }
        long otherAllocation = existing.stream()
                .filter(ticket -> ticket.getStatus() != TicketTypeStatus.ARCHIVED)
                .filter(ticket -> current == null || !ticket.getId().equals(current.getId()))
                .mapToLong(TicketType::getAllocation)
                .sum();
        if (otherAllocation + request.allocation() > event.getCapacity()) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "TICKET_ALLOCATION_EXCEEDS_EVENT_CAPACITY",
                    "Combined ticket allocation cannot exceed event capacity");
        }
    }

    private void appendChanged(TicketType ticket, RequestContext context, Instant now) {
        outbox.append(
                "TicketType",
                ticket.getId(),
                EventLifecycleEvents.TICKET_TYPE_CHANGED,
                EventLifecycleEvents.VERSION,
                new EventLifecycleEvents.TicketTypeChangedV1(
                        ticket.getEventId(),
                        ticket.getId(),
                        ticket.getName(),
                        ticket.getPrice(),
                        ticket.getCurrency(),
                        ticket.getAllocation(),
                        ticket.getSalesStart(),
                        ticket.getSalesEnd(),
                        ticket.getStatus(),
                        now),
                context,
                now);
    }

    private ManagedEvent requiredEventForUpdate(UUID eventId) {
        return eventRepository.findByIdForUpdate(eventId).orElseThrow(() -> new EventApiException(
                HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "The event was not found"));
    }

    private TicketType requiredTicket(UUID ticketTypeId, UUID eventId) {
        return ticketRepository.findByIdAndEventId(ticketTypeId, eventId).orElseThrow(() -> new EventApiException(
                HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "The ticket type was not found"));
    }

    private void requireOwner(ManagedEvent event, AuthenticatedActor actor) {
        if (!actor.isAdmin() && !actor.owns(event.getOrganizerId())) {
            throw new EventApiException(HttpStatus.FORBIDDEN, "EVENT_OWNERSHIP_REQUIRED", "Event ownership is required");
        }
    }

    private void requireMutable(ManagedEvent event) {
        if (!event.isTicketConfigurationMutable()) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "TICKET_CONFIGURATION_LOCKED",
                    "Ticket products cannot change after sales open or the event becomes terminal");
        }
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
