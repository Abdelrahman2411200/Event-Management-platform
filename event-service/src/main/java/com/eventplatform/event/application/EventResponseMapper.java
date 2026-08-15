package com.eventplatform.event.application;

import com.eventplatform.event.api.EventApi;
import com.eventplatform.event.domain.EventCategory;
import com.eventplatform.event.domain.ManagedEvent;
import com.eventplatform.event.domain.TicketType;
import com.eventplatform.event.domain.TicketTypeRepository;
import com.eventplatform.event.domain.TicketTypeStatus;
import java.time.Instant;
import java.util.EnumSet;
import org.springframework.stereotype.Component;

@Component
public class EventResponseMapper {

    private final EventCategoryService categoryService;
    private final TicketTypeRepository ticketRepository;

    public EventResponseMapper(
            EventCategoryService categoryService,
            TicketTypeRepository ticketRepository) {
        this.categoryService = categoryService;
        this.ticketRepository = ticketRepository;
    }

    public EventApi.EventResponse management(ManagedEvent event) {
        EventCategory category = categoryService.required(event.getCategoryId());
        return new EventApi.EventResponse(
                event.getId(),
                event.getOrganizerId(),
                event.getTitle(),
                event.getDescription(),
                categoryService.toResponse(category),
                event.getTimezone(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getVenueId(),
                event.getVenueSpaceId(),
                event.getVenueReservationId(),
                event.getCapacity(),
                event.getStatus(),
                ticketRepository.findAllByEventIdOrderByPriceAscNameAsc(event.getId()).stream()
                        .map(this::ticket)
                        .toList(),
                event.getPublishedAt(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }

    public EventApi.PublicEventSummary summary(ManagedEvent event) {
        return new EventApi.PublicEventSummary(
                event.getId(),
                event.getTitle(),
                categoryService.toResponse(categoryService.required(event.getCategoryId())),
                event.getTimezone(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getVenueId(),
                event.getVenueSpaceId(),
                event.getCapacity(),
                event.getStatus());
    }

    public EventApi.PublicEventDetail publicDetail(ManagedEvent event) {
        Instant now = Instant.now();
        return new EventApi.PublicEventDetail(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                categoryService.toResponse(categoryService.required(event.getCategoryId())),
                event.getTimezone(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getVenueId(),
                event.getVenueSpaceId(),
                event.getCapacity(),
                event.getStatus(),
                ticketRepository.findAllByEventIdAndStatusInOrderByPriceAscNameAsc(
                                event.getId(), EnumSet.of(TicketTypeStatus.ACTIVE, TicketTypeStatus.PAUSED)).stream()
                        .map(ticket -> publicTicket(ticket, event, now))
                        .toList(),
                event.getPublishedAt());
    }

    public EventApi.TicketTypeResponse ticket(TicketType ticket) {
        return new EventApi.TicketTypeResponse(
                ticket.getId(),
                ticket.getEventId(),
                ticket.getName(),
                ticket.getDescription(),
                ticket.getPrice(),
                ticket.getCurrency(),
                ticket.getAllocation(),
                ticket.getReservedQuantity(),
                ticket.availableQuantity(),
                ticket.getSalesStart(),
                ticket.getSalesEnd(),
                ticket.getMinQuantity(),
                ticket.getMaxQuantity(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    private EventApi.PublicTicketTypeResponse publicTicket(TicketType ticket, ManagedEvent event, Instant now) {
        return new EventApi.PublicTicketTypeResponse(
                ticket.getId(),
                ticket.getName(),
                ticket.getDescription(),
                ticket.getPrice(),
                ticket.getCurrency(),
                ticket.getAllocation(),
                ticket.availableQuantity(),
                ticket.getSalesStart(),
                ticket.getSalesEnd(),
                ticket.getMinQuantity(),
                ticket.getMaxQuantity(),
                ticket.getStatus(),
                event.getStatus() == com.eventplatform.event.domain.EventStatus.SALES_OPEN
                        && ticket.isOnSale(now));
    }
}
