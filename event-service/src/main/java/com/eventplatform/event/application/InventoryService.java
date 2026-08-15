package com.eventplatform.event.application;

import com.eventplatform.event.api.EventApi;
import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.domain.EventStatus;
import com.eventplatform.event.domain.InventoryReservation;
import com.eventplatform.event.domain.InventoryReservationRepository;
import com.eventplatform.event.domain.InventoryReservationStatus;
import com.eventplatform.event.domain.ManagedEvent;
import com.eventplatform.event.domain.ManagedEventRepository;
import com.eventplatform.event.domain.TicketType;
import com.eventplatform.event.domain.TicketTypeRepository;
import com.eventplatform.event.security.AuthenticatedActor;
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
    private final Duration holdTtl;

    public InventoryService(
            ManagedEventRepository eventRepository,
            TicketTypeRepository ticketRepository,
            InventoryReservationRepository reservationRepository,
            PublicEventCache cache,
            @Value("${platform.inventory.hold-ttl:15m}") Duration holdTtl) {
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.reservationRepository = reservationRepository;
        this.cache = cache;
        this.holdTtl = holdTtl;
        if (holdTtl.compareTo(Duration.ofMinutes(1)) < 0 || holdTtl.compareTo(Duration.ofHours(2)) > 0) {
            throw new IllegalArgumentException("Inventory hold TTL must be between 1 minute and 2 hours");
        }
    }

    @Transactional
    public EventApi.InventoryAvailabilityResponse availability(UUID eventId, UUID ticketTypeId) {
        ManagedEvent event = requiredEvent(eventId);
        TicketType ticket = requiredTicketForUpdate(eventId, ticketTypeId);
        if (expireForTicket(ticket, Instant.now())) {
            cache.evict(eventId);
        }
        return availabilityResponse(event, ticket, Instant.now());
    }

    @Transactional
    public EventApi.InventoryReservationResponse reserve(
            UUID eventId,
            UUID ticketTypeId,
            int quantity,
            String idempotencyKey,
            AuthenticatedActor actor) {
        ManagedEvent event = requiredEvent(eventId);
        if (event.getStatus() != EventStatus.SALES_OPEN) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "EVENT_SALES_NOT_OPEN",
                    "Ticket inventory can be reserved only while event sales are open");
        }
        TicketType ticket = requiredTicketForUpdate(eventId, ticketTypeId);
        Instant now = Instant.now();
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
            return reservationResponse(existing, ticket);
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
        cache.evict(eventId);
        return reservationResponse(reservation, ticket);
    }

    @Transactional
    public EventApi.InventoryReservationResponse release(
            UUID eventId,
            UUID ticketTypeId,
            UUID reservationId,
            String idempotencyKey,
            AuthenticatedActor actor) {
        InventoryReservation keyOwner = reservationRepository.findByReleaseIdempotencyKey(idempotencyKey).orElse(null);
        if (keyOwner != null && !keyOwner.getId().equals(reservationId)) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used for another inventory release");
        }
        TicketType ticket = requiredTicketForUpdate(eventId, ticketTypeId);
        Instant now = Instant.now();
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
        if (reservation.getStatus() == InventoryReservationStatus.ACTIVE) {
            ticket.release(reservation.getQuantity(), now);
        }
        reservation.release(idempotencyKey, now);
        cache.evict(eventId);
        return reservationResponse(reservation, ticket);
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
        Instant now = Instant.now();
        if (reservation != null
                && reservation.getStatus() == InventoryReservationStatus.ACTIVE
                && !reservation.getExpiresAt().isAfter(now)) {
            ticket.release(reservation.getQuantity(), now);
            reservation.expire(now);
            cache.evict(reservation.getEventId());
        }
    }

    private boolean expireForTicket(TicketType ticket, Instant now) {
        List<InventoryReservation> expired = reservationRepository.findExpiredActive(ticket.getId(), now);
        for (InventoryReservation reservation : expired) {
            ticket.release(reservation.getQuantity(), now);
            reservation.expire(now);
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
            TicketType ticket) {
        return new EventApi.InventoryReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getTicketTypeId(),
                reservation.getRequesterId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                ticket.availableQuantity(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
