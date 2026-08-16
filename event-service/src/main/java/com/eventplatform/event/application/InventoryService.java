package com.eventplatform.event.application;

import com.eventplatform.event.api.EventApi;
import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.api.RequestContext;
import com.eventplatform.event.domain.EventStatus;
import com.eventplatform.event.domain.InventoryReservation;
import com.eventplatform.event.domain.InventoryReservationRepository;
import com.eventplatform.event.domain.InventoryReservationStatus;
import com.eventplatform.event.domain.ManagedEvent;
import com.eventplatform.event.domain.ManagedEventRepository;
import com.eventplatform.event.domain.TicketType;
import com.eventplatform.event.domain.TicketTypeRepository;
import com.eventplatform.event.integration.EventLifecycleEvents;
import com.eventplatform.event.outbox.TransactionalOutbox;
import com.eventplatform.event.security.AuthenticatedActor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final ManagedEventRepository eventRepository;
    private final TicketTypeRepository ticketRepository;
    private final InventoryReservationRepository reservationRepository;
    private final PublicEventCache cache;
    private final TransactionalOutbox outbox;
    private final Clock clock;
    private final Duration holdTtl;

    public InventoryService(
            ManagedEventRepository eventRepository,
            TicketTypeRepository ticketRepository,
            InventoryReservationRepository reservationRepository,
            PublicEventCache cache,
            TransactionalOutbox outbox,
            Clock clock,
            @Value("${platform.inventory.hold-ttl:15m}") Duration holdTtl) {
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.reservationRepository = reservationRepository;
        this.cache = cache;
        this.outbox = outbox;
        this.clock = clock;
        this.holdTtl = holdTtl;
        if (holdTtl.compareTo(Duration.ofMinutes(1)) < 0 || holdTtl.compareTo(Duration.ofHours(2)) > 0) {
            throw new IllegalArgumentException("Inventory hold TTL must be between 1 minute and 2 hours");
        }
    }

    @Transactional
    public EventApi.InventoryAvailabilityResponse availability(UUID eventId, UUID ticketTypeId) {
        ManagedEvent event = requiredEvent(eventId);
        TicketType ticket = requiredTicketForUpdate(eventId, ticketTypeId);
        Instant availabilityTime = clock.instant();
        if (expireForTicket(ticket, availabilityTime)) {
            cache.evict(eventId);
        }
        return availabilityResponse(event, ticket, availabilityTime);
    }

    @Transactional
    public EventApi.InventoryReservationResponse reserve(
            UUID eventId,
            UUID ticketTypeId,
            int quantity,
            String idempotencyKey,
            AuthenticatedActor actor) {
        return reserve(
                eventId,
                ticketTypeId,
                quantity,
                idempotencyKey,
                actor,
                systemContext("inventory-reserve", idempotencyKey));
    }

    @Transactional
    public EventApi.InventoryReservationResponse reserve(
            UUID eventId,
            UUID ticketTypeId,
            int quantity,
            String idempotencyKey,
            AuthenticatedActor actor,
        RequestContext requestContext) {
        ManagedEvent event = requiredEvent(eventId);
        TicketType ticket = requiredTicketForUpdate(eventId, ticketTypeId);
        Instant now = clock.instant();
        boolean expired = expireForTicket(ticket, now);

        InventoryReservation existing = reservationRepository.findByReserveIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.matches(eventId, ticketTypeId, actor.userId(), quantity)) {
                throw new EventApiException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used with different inventory input");
            }
            if (expired) {
                cache.evict(eventId);
            }
            return reservationResponse(existing, event, ticket);
        }
        if (event.getStatus() != EventStatus.SALES_OPEN) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_SALES_NOT_OPEN",
                    "Ticket inventory can be reserved only while event sales are open");
        }
        if (!ticket.isOnSale(now)) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "TICKET_TYPE_NOT_ON_SALE",
                    "The ticket type is not currently within its active sales window");
        }
        if (quantity < ticket.getMinQuantity() || quantity > ticket.getMaxQuantity()) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "TICKET_QUANTITY_OUT_OF_RANGE",
                    "Requested quantity is outside the ticket type booking limits");
        }
        if (quantity > ticket.availableQuantity()) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_TICKET_INVENTORY",
                    "Requested quantity exceeds currently available ticket inventory");
        }
        ticket.reserve(quantity, now);
        InventoryReservation reservation = new InventoryReservation(
                UUID.randomUUID(),
                eventId,
                ticketTypeId,
                actor.userId(),
                quantity,
                idempotencyKey,
                now.plus(holdTtl),
                now);
        reservationRepository.save(reservation);
        appendInventoryEvent(
                EventLifecycleEvents.INVENTORY_HELD,
                reservation,
                requestContext,
                now);
        cache.evict(eventId);
        return reservationResponse(reservation, event, ticket);
    }

    @Transactional
    public EventApi.InventoryReservationResponse release(
            UUID eventId,
            UUID ticketTypeId,
            UUID reservationId,
            String idempotencyKey,
            AuthenticatedActor actor) {
        return release(
                eventId,
                ticketTypeId,
                reservationId,
                idempotencyKey,
                actor,
                systemContext("inventory-release", idempotencyKey));
    }

    @Transactional
    public EventApi.InventoryReservationResponse release(
            UUID eventId,
            UUID ticketTypeId,
            UUID reservationId,
            String idempotencyKey,
            AuthenticatedActor actor,
            RequestContext requestContext) {
        InventoryReservation keyOwner = reservationRepository.findByReleaseIdempotencyKey(idempotencyKey).orElse(null);
        if (keyOwner != null && !keyOwner.getId().equals(reservationId)) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used for another inventory release");
        }
        TicketType ticket = requiredTicketForUpdate(eventId, ticketTypeId);
        Instant now = clock.instant();
        expireForTicket(ticket, now);
        InventoryReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .filter(candidate -> candidate.getEventId().equals(eventId)
                        && candidate.getTicketTypeId().equals(ticketTypeId))
                .orElseThrow(() -> new EventApiException(
                        HttpStatus.NOT_FOUND,
                        "INVENTORY_RESERVATION_NOT_FOUND",
                        "The inventory reservation was not found"));
        if (!actor.isAdmin() && !reservation.getRequesterId().equals(actor.userId())) {
            throw new EventApiException(
                    HttpStatus.FORBIDDEN,
                    "INVENTORY_RESERVATION_OWNERSHIP_REQUIRED",
                    "Only the reservation requester or an administrator may release this inventory");
        }
        if (reservation.getStatus() == InventoryReservationStatus.CONFIRMED) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "INVENTORY_RESERVATION_CONFIRMED",
                    "Confirmed inventory cannot be released by the hold-release operation");
        }
        if (reservation.getStatus() == InventoryReservationStatus.ACTIVE) {
            ticket.release(reservation.getQuantity(), now);
            reservation.release(idempotencyKey, now);
            appendInventoryEvent(
                    EventLifecycleEvents.INVENTORY_RELEASED,
                    reservation,
                    requestContext,
                    now);
        } else {
            reservation.release(idempotencyKey, now);
        }
        cache.evict(eventId);
        return reservationResponse(reservation, requiredEvent(eventId), ticket);
    }

    @Transactional
    public EventApi.InventoryReservationResponse confirm(
            UUID eventId,
            UUID ticketTypeId,
            UUID reservationId,
            String idempotencyKey,
            AuthenticatedActor actor,
            RequestContext requestContext) {
        InventoryReservation keyOwner = reservationRepository
                .findByConfirmationIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (keyOwner != null && !keyOwner.getId().equals(reservationId)) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used for another inventory confirmation");
        }
        TicketType ticket = requiredTicketForUpdate(eventId, ticketTypeId);
        Instant now = clock.instant();
        expireForTicket(ticket, now);
        InventoryReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .filter(candidate -> candidate.getEventId().equals(eventId)
                        && candidate.getTicketTypeId().equals(ticketTypeId))
                .orElseThrow(() -> new EventApiException(
                        HttpStatus.NOT_FOUND,
                        "INVENTORY_RESERVATION_NOT_FOUND",
                        "The inventory reservation was not found"));
        if (!actor.isAdmin() && !reservation.getRequesterId().equals(actor.userId())) {
            throw new EventApiException(
                    HttpStatus.FORBIDDEN,
                    "INVENTORY_RESERVATION_OWNERSHIP_REQUIRED",
                    "Only the reservation requester or an administrator may confirm this inventory");
        }
        if (reservation.getStatus() == InventoryReservationStatus.CONFIRMED) {
            if (!idempotencyKey.equals(reservation.getConfirmationIdempotencyKey())) {
                throw new EventApiException(
                        HttpStatus.CONFLICT,
                        "INVENTORY_ALREADY_CONFIRMED",
                        "The inventory reservation was already confirmed by another command");
            }
            return reservationResponse(reservation, requiredEvent(eventId), ticket);
        }
        if (reservation.getStatus() != InventoryReservationStatus.ACTIVE) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "INVENTORY_RESERVATION_NOT_ACTIVE",
                    "Only an active, unexpired inventory reservation can be confirmed");
        }
        reservation.confirm(idempotencyKey, now);
        appendInventoryEvent(
                EventLifecycleEvents.INVENTORY_CONFIRMED,
                reservation,
                requestContext,
                now);
        cache.evict(eventId);
        return reservationResponse(reservation, requiredEvent(eventId), ticket);
    }

    @Transactional
    public void expireReservation(UUID reservationId) {
        InventoryReservation snapshot = reservationRepository.findById(reservationId).orElse(null);
        if (snapshot == null || snapshot.getStatus() != InventoryReservationStatus.ACTIVE) {
            return;
        }
        TicketType ticket = ticketRepository.findByIdAndEventIdForUpdate(
                        snapshot.getTicketTypeId(), snapshot.getEventId())
                .orElse(null);
        if (ticket == null) {
            return;
        }
        InventoryReservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
        Instant now = clock.instant();
        if (reservation != null
                && reservation.getStatus() == InventoryReservationStatus.ACTIVE
                && !reservation.getExpiresAt().isAfter(now)) {
            ticket.release(reservation.getQuantity(), now);
            reservation.expire(now);
            appendInventoryEvent(
                    EventLifecycleEvents.INVENTORY_EXPIRED,
                    reservation,
                    systemContext("inventory-expiry", reservation.getId().toString()),
                    now);
            cache.evict(reservation.getEventId());
        }
    }

    @Transactional
    public void confirmSaga(
            UUID bookingId, UUID paymentId, UUID attendeeId, UUID eventId,
            UUID ticketTypeId, UUID reservationId, String commandKey, RequestContext context) {
        try {
            InventoryReservation existing = reservationRepository.findById(reservationId).orElse(null);
            if (existing != null
                    && existing.getStatus() == InventoryReservationStatus.CONFIRMED
                    && commandKey.equals(existing.getConfirmationIdempotencyKey())) {
                appendInventoryEvent(EventLifecycleEvents.INVENTORY_CONFIRMED, existing, context, clock.instant());
                return;
            }
            confirm(eventId, ticketTypeId, reservationId, commandKey,
                    new AuthenticatedActor(attendeeId, java.util.Set.of()), context);
        } catch (EventApiException exception) {
            appendSagaRejection(EventLifecycleEvents.INVENTORY_CONFIRMATION_REJECTED,
                    bookingId, paymentId, reservationId, eventId, ticketTypeId, commandKey,
                    exception.getCode(), exception.getMessage(), context);
        }
    }

    @Transactional
    public void releaseSaga(
            UUID bookingId, UUID paymentId, UUID attendeeId, UUID eventId,
            UUID ticketTypeId, UUID reservationId, String commandKey, RequestContext context) {
        try {
            release(eventId, ticketTypeId, reservationId, commandKey,
                    new AuthenticatedActor(attendeeId, java.util.Set.of()), context);
        } catch (EventApiException exception) {
            appendSagaRejection(EventLifecycleEvents.INVENTORY_RELEASE_REJECTED,
                    bookingId, paymentId, reservationId, eventId, ticketTypeId, commandKey,
                    exception.getCode(), exception.getMessage(), context);
        }
    }

    private void appendSagaRejection(
            String eventType, UUID bookingId, UUID paymentId, UUID reservationId,
            UUID eventId, UUID ticketTypeId, String commandKey, String code,
            String reason, RequestContext context) {
        Instant now = clock.instant();
        outbox.append("InventoryReservation", reservationId, eventType, EventLifecycleEvents.VERSION,
                new EventLifecycleEvents.InventorySagaRejectedV1(
                        bookingId, paymentId, reservationId, eventId, ticketTypeId,
                        commandKey, code, reason, now), context, now);
    }

    private boolean expireForTicket(TicketType ticket, Instant now) {
        List<InventoryReservation> expired = reservationRepository.findExpiredActive(ticket.getId(), now);
        for (InventoryReservation reservation : expired) {
            ticket.release(reservation.getQuantity(), now);
            reservation.expire(now);
            appendInventoryEvent(
                    EventLifecycleEvents.INVENTORY_EXPIRED,
                    reservation,
                    systemContext("inventory-expiry", reservation.getId().toString()),
                    now);
        }
        return !expired.isEmpty();
    }

    private ManagedEvent requiredEvent(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new EventApiException(
                HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "The event was not found"));
    }

    private TicketType requiredTicketForUpdate(UUID eventId, UUID ticketTypeId) {
        return ticketRepository.findByIdAndEventIdForUpdate(ticketTypeId, eventId)
                .orElseThrow(() -> new EventApiException(
                        HttpStatus.NOT_FOUND,
                        "TICKET_TYPE_NOT_FOUND",
                        "The ticket type was not found"));
    }

    private EventApi.InventoryAvailabilityResponse availabilityResponse(
            ManagedEvent event,
            TicketType ticket,
            Instant now) {
        return new EventApi.InventoryAvailabilityResponse(
                event.getId(),
                ticket.getId(),
                ticket.getAllocation(),
                ticket.getReservedQuantity(),
                ticket.availableQuantity(),
                event.getStatus() == EventStatus.SALES_OPEN && ticket.isOnSale(now),
                ticket.getSalesStart(),
                ticket.getSalesEnd());
    }

    private EventApi.InventoryReservationResponse reservationResponse(
            InventoryReservation reservation,
            ManagedEvent event,
            TicketType ticket) {
        return new EventApi.InventoryReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                event.getOrganizerId(),
                event.getTitle(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getVenueId(),
                event.getVenueSpaceId(),
                reservation.getTicketTypeId(),
                ticket.getName(),
                ticket.getPrice(),
                ticket.getCurrency(),
                reservation.getRequesterId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getConfirmedAt(),
                ticket.availableQuantity(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }

    private void appendInventoryEvent(
            String eventType,
            InventoryReservation reservation,
            RequestContext context,
            Instant occurredAt) {
        outbox.append(
                "InventoryReservation",
                reservation.getId(),
                eventType,
                EventLifecycleEvents.VERSION,
                new EventLifecycleEvents.InventoryReservationChangedV1(
                        reservation.getId(),
                        reservation.getEventId(),
                        reservation.getTicketTypeId(),
                        reservation.getRequesterId(),
                        reservation.getQuantity(),
                        reservation.getStatus(),
                        reservation.getExpiresAt(),
                        occurredAt),
                context,
                occurredAt);
    }

    private RequestContext systemContext(String operation, String reference) {
        String normalized = reference.replaceAll("[^A-Za-z0-9._:-]", "-");
        String correlationId = operation + ":" + normalized;
        return new RequestContext(correlationId.substring(0, Math.min(correlationId.length(), 128)), null);
    }
}
