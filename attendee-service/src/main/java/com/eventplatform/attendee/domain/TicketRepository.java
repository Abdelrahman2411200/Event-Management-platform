package com.eventplatform.attendee.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findAllByBookingIdOrderByIssuedAt(UUID bookingId);
    List<Ticket> findAllByAttendeeIdOrderByEventStartsAtAsc(UUID attendeeId);
    List<Ticket> findAllByAttendeeIdAndEventStartsAtGreaterThanEqualAndStatusInOrderByEventStartsAtAsc(
            UUID attendeeId, Instant startsAt, Collection<TicketStatus> statuses);
    List<Ticket> findAllByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.bookingId = :bookingId order by ticket.issuedAt")
    List<Ticket> findAllByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.eventId = :eventId")
    List<Ticket> findAllByEventIdForUpdate(@Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") UUID id);
}
