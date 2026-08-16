package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.AttendeeApi;
import com.eventplatform.attendee.api.AttendeeApiException;
import com.eventplatform.attendee.api.RequestContext;
import com.eventplatform.attendee.domain.CheckIn;
import com.eventplatform.attendee.domain.CheckInAttempt;
import com.eventplatform.attendee.domain.CheckInAttemptRepository;
import com.eventplatform.attendee.domain.CheckInRepository;
import com.eventplatform.attendee.domain.ScanOperation;
import com.eventplatform.attendee.domain.ScanOutcome;
import com.eventplatform.attendee.domain.Ticket;
import com.eventplatform.attendee.domain.TicketRepository;
import com.eventplatform.attendee.domain.TicketStatus;
import com.eventplatform.attendee.integration.AttendeeLifecycleEvents;
import com.eventplatform.attendee.outbox.AttendeeTransactionalOutbox;
import com.eventplatform.attendee.security.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketScanService {
    private final TicketRepository ticketRepository;
    private final CheckInRepository checkInRepository;
    private final CheckInAttemptRepository attemptRepository;
    private final QrTokenService qrTokenService;
    private final AttendeeTransactionalOutbox outbox;
    private final Clock clock;

    public TicketScanService(
            TicketRepository ticketRepository,
            CheckInRepository checkInRepository,
            CheckInAttemptRepository attemptRepository,
            QrTokenService qrTokenService,
            AttendeeTransactionalOutbox outbox,
            Clock clock) {
        this.ticketRepository = ticketRepository;
        this.checkInRepository = checkInRepository;
        this.attemptRepository = attemptRepository;
        this.qrTokenService = qrTokenService;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public AttendeeApi.ScanResponse validate(
            AttendeeApi.ScanRequest request, String idempotencyKey,
            AuthenticatedActor actor, RequestContext context) {
        return scan(request, idempotencyKey, actor, context, ScanOperation.VALIDATE);
    }

    @Transactional
    public AttendeeApi.ScanResponse checkIn(
            AttendeeApi.ScanRequest request, String idempotencyKey,
            AuthenticatedActor actor, RequestContext context) {
        return scan(request, idempotencyKey, actor, context, ScanOperation.CHECK_IN);
    }

    private AttendeeApi.ScanResponse scan(
            AttendeeApi.ScanRequest request, String idempotencyKey,
            AuthenticatedActor actor, RequestContext context, ScanOperation operation) {
        String fingerprint = qrTokenService.fingerprint(request.qrToken());
        CheckInAttempt existing = attemptRepository
                .findByScannerIdAndOperationAndIdempotencyKey(actor.userId(), operation, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.getEventId().equals(request.eventId())
                    || !existing.getTokenFingerprint().equals(fingerprint)) {
                throw new AttendeeApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used with different scan input");
            }
            return response(existing, existing.getTicketId() == null
                    ? null : ticketRepository.findById(existing.getTicketId()).orElse(null));
        }

        Instant now = clock.instant();
        QrTokenService.QrPayload payload;
        try {
            payload = qrTokenService.verify(request.qrToken());
        } catch (QrTokenService.QrVerificationException exception) {
            return record(operation, exception.outcome(), actor.userId(), request.eventId(), null,
                    fingerprint, idempotencyKey, null, now, null);
        }
        if (!request.eventId().equals(payload.eventId())) {
            return record(operation, ScanOutcome.WRONG_EVENT, actor.userId(), request.eventId(), payload.ticketId(),
                    fingerprint, idempotencyKey, null, now, null);
        }

        Ticket ticket = ticketRepository.findByIdForUpdate(payload.ticketId()).orElse(null);
        if (ticket == null) {
            return record(operation, ScanOutcome.TICKET_NOT_FOUND, actor.userId(), request.eventId(), payload.ticketId(),
                    fingerprint, idempotencyKey, null, now, null);
        }
        // A concurrent retry may have committed while this transaction waited for the
        // ticket lock. Re-read the durable command before applying a second scan.
        CheckInAttempt committedRetry = attemptRepository
                .findByScannerIdAndOperationAndIdempotencyKey(actor.userId(), operation, idempotencyKey)
                .orElse(null);
        if (committedRetry != null) {
            if (!committedRetry.getEventId().equals(request.eventId())
                    || !committedRetry.getTokenFingerprint().equals(fingerprint)) {
                throw new AttendeeApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used with different scan input");
            }
            return response(committedRetry, ticket);
        }
        if (!ticket.getEventId().equals(payload.eventId())) {
            return record(operation, ScanOutcome.TAMPERED_TOKEN, actor.userId(), request.eventId(), ticket.getId(),
                    fingerprint, idempotencyKey, ticket.getCheckedInAt(), now, ticket);
        }
        if (!actor.mayScan(ticket.getEventOrganizerId())) {
            return record(operation, ScanOutcome.ORGANIZER_NOT_OWNER, actor.userId(), request.eventId(), ticket.getId(),
                    fingerprint, idempotencyKey, ticket.getCheckedInAt(), now, ticket);
        }
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            return record(operation, ScanOutcome.TICKET_CANCELLED, actor.userId(), request.eventId(), ticket.getId(),
                    fingerprint, idempotencyKey, ticket.getCheckedInAt(), now, ticket);
        }
        if (ticket.getStatus() == TicketStatus.REFUNDED) {
            return record(operation, ScanOutcome.TICKET_REFUNDED, actor.userId(), request.eventId(), ticket.getId(),
                    fingerprint, idempotencyKey, ticket.getCheckedInAt(), now, ticket);
        }
        if (ticket.getTokenVersion() != payload.tokenVersion()) {
            return record(operation, ScanOutcome.TAMPERED_TOKEN, actor.userId(), request.eventId(), ticket.getId(),
                    fingerprint, idempotencyKey, ticket.getCheckedInAt(), now, ticket);
        }
        if (ticket.getStatus() == TicketStatus.CHECKED_IN) {
            return record(operation, ScanOutcome.ALREADY_CHECKED_IN, actor.userId(), request.eventId(), ticket.getId(),
                    fingerprint, idempotencyKey, ticket.getCheckedInAt(), now, ticket);
        }
        if (ticket.getStatus() != TicketStatus.ISSUED) {
            return record(operation, ScanOutcome.TICKET_NOT_ISSUED, actor.userId(), request.eventId(), ticket.getId(),
                    fingerprint, idempotencyKey, ticket.getCheckedInAt(), now, ticket);
        }
        if (operation == ScanOperation.VALIDATE) {
            return record(operation, ScanOutcome.VALID, actor.userId(), request.eventId(), ticket.getId(),
                    fingerprint, idempotencyKey, null, now, ticket);
        }

        ticket.checkIn(now);
        checkInRepository.save(new CheckIn(UUID.randomUUID(), ticket.getId(), ticket.getEventId(), actor.userId(), now));
        outbox.append("Ticket", ticket.getId(), AttendeeLifecycleEvents.TICKET_CHECKED_IN,
                AttendeeLifecycleEvents.VERSION,
                new AttendeeLifecycleEvents.TicketCheckedInV1(
                        ticket.getId(), ticket.getBookingId(), ticket.getAttendeeId(), ticket.getEventId(),
                        actor.userId(), now), context, now);
        return record(operation, ScanOutcome.CHECKED_IN, actor.userId(), request.eventId(), ticket.getId(),
                fingerprint, idempotencyKey, now, now, ticket);
    }

    private AttendeeApi.ScanResponse record(
            ScanOperation operation, ScanOutcome outcome, UUID scannerId, UUID eventId, UUID ticketId,
            String fingerprint, String idempotencyKey, Instant checkedInAt, Instant attemptedAt, Ticket ticket) {
        CheckInAttempt attempt = new CheckInAttempt(
                UUID.randomUUID(), operation, outcome, scannerId, eventId, ticketId,
                fingerprint, idempotencyKey, checkedInAt, attemptedAt);
        attemptRepository.save(attempt);
        return response(attempt, ticket);
    }

    private AttendeeApi.ScanResponse response(CheckInAttempt attempt, Ticket ticket) {
        boolean accepted = attempt.getOutcome() == ScanOutcome.VALID
                || attempt.getOutcome() == ScanOutcome.CHECKED_IN
                || attempt.getOutcome() == ScanOutcome.ALREADY_CHECKED_IN;
        return new AttendeeApi.ScanResponse(
                accepted, attempt.getOutcome(), attempt.getEventId(), attempt.getTicketId(),
                ticket == null ? null : ticket.getStatus(), attempt.getTicketCheckedInAt(), attempt.getAttemptedAt());
    }
}
