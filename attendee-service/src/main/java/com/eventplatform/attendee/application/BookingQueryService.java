package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.AttendeeApi;
import com.eventplatform.attendee.api.AttendeeApiException;
import com.eventplatform.attendee.domain.Booking;
import com.eventplatform.attendee.domain.BookingRepository;
import com.eventplatform.attendee.domain.TicketRepository;
import com.eventplatform.attendee.domain.TicketStatus;
import com.eventplatform.attendee.security.AuthenticatedActor;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingQueryService {
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final BookingResponseMapper mapper;
    private final Clock clock;

    public BookingQueryService(
            BookingRepository bookingRepository, TicketRepository ticketRepository,
            BookingResponseMapper mapper, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AttendeeApi.BookingResponse get(UUID bookingId, AuthenticatedActor actor) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new AttendeeApiException(
                HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "The booking was not found"));
        requireOwner(booking, actor);
        return mapper.booking(booking);
    }

    @Transactional(readOnly = true)
    public List<AttendeeApi.BookingResponse> history(AuthenticatedActor actor) {
        return bookingRepository.findAllByAttendeeIdOrderByCreatedAtDesc(actor.userId()).stream()
                .map(mapper::booking).toList();
    }

    @Transactional(readOnly = true)
    public AttendeeApi.TicketListResponse tickets(AuthenticatedActor actor, boolean upcomingOnly) {
        var tickets = upcomingOnly
                ? ticketRepository.findAllByAttendeeIdAndEventStartsAtGreaterThanEqualAndStatusInOrderByEventStartsAtAsc(
                        actor.userId(), clock.instant(), EnumSet.of(TicketStatus.ISSUED, TicketStatus.CHECKED_IN))
                : ticketRepository.findAllByAttendeeIdOrderByEventStartsAtAsc(actor.userId());
        return new AttendeeApi.TicketListResponse(tickets.stream().map(mapper::ticket).toList());
    }

    private void requireOwner(Booking booking, AuthenticatedActor actor) {
        if (!actor.owns(booking.getAttendeeId())) {
            throw new AttendeeApiException(HttpStatus.FORBIDDEN, "BOOKING_OWNERSHIP_REQUIRED",
                    "Only the booking owner may read this booking");
        }
    }
}
