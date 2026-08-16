package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.AttendeeApiException;
import com.eventplatform.attendee.domain.BookingCommand;
import com.eventplatform.attendee.domain.BookingCommandRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BookingCommandService {
    private final BookingCommandRepository repository;
    private final Clock clock;

    public BookingCommandService(BookingCommandRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public BookingCommand claim(
            UUID attendeeId, String idempotencyKey, UUID eventId, UUID ticketTypeId, int quantity) {
        BookingCommand existing = repository.findByAttendeeIdAndIdempotencyKey(attendeeId, idempotencyKey).orElse(null);
        if (existing != null) return validate(existing, eventId, ticketTypeId, quantity);
        BookingCommand candidate = new BookingCommand(
                UUID.randomUUID(), attendeeId, idempotencyKey, eventId, ticketTypeId,
                quantity, UUID.randomUUID(), clock.instant());
        try {
            return repository.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException exception) {
            BookingCommand winner = repository.findByAttendeeIdAndIdempotencyKey(attendeeId, idempotencyKey)
                    .orElseThrow(() -> exception);
            return validate(winner, eventId, ticketTypeId, quantity);
        }
    }

    private BookingCommand validate(BookingCommand command, UUID eventId, UUID ticketTypeId, int quantity) {
        if (!command.matches(eventId, ticketTypeId, quantity)) {
            throw new AttendeeApiException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with different booking input");
        }
        return command;
    }
}
