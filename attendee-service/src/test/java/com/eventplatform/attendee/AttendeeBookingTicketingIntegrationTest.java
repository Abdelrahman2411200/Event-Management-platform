package com.eventplatform.attendee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventplatform.attendee.api.AttendeeApi;
import com.eventplatform.attendee.api.AttendeeApiException;
import com.eventplatform.attendee.api.RequestContext;
import com.eventplatform.attendee.application.BookingCoordinator;
import com.eventplatform.attendee.application.BookingPersistenceService;
import com.eventplatform.attendee.application.BookingQueryService;
import com.eventplatform.attendee.application.EventInventoryPort;
import com.eventplatform.attendee.application.TicketScanService;
import com.eventplatform.attendee.domain.AttendeeProfileRepository;
import com.eventplatform.attendee.domain.BookingCommandRepository;
import com.eventplatform.attendee.domain.BookingLineItemRepository;
import com.eventplatform.attendee.domain.BookingRepository;
import com.eventplatform.attendee.domain.BookingStatus;
import com.eventplatform.attendee.domain.CheckInAttemptRepository;
import com.eventplatform.attendee.domain.CheckInRepository;
import com.eventplatform.attendee.domain.RegistrationRepository;
import com.eventplatform.attendee.domain.ScanOutcome;
import com.eventplatform.attendee.domain.TicketHoldRepository;
import com.eventplatform.attendee.domain.TicketHoldStatus;
import com.eventplatform.attendee.domain.TicketRepository;
import com.eventplatform.attendee.integration.ProcessedIntegrationEventRepository;
import com.eventplatform.attendee.outbox.AttendeeOutboxRepository;
import com.eventplatform.attendee.security.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AttendeeBookingTicketingIntegrationTest {
    private static final UUID ATTENDEE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ATTENDEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORGANIZER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final AuthenticatedActor ATTENDEE = new AuthenticatedActor(ATTENDEE_ID, Set.of("ATTENDEE"));
    private static final AuthenticatedActor OTHER_ATTENDEE = new AuthenticatedActor(OTHER_ATTENDEE_ID, Set.of("ATTENDEE"));
    private static final AuthenticatedActor STAFF = new AuthenticatedActor(UUID.randomUUID(), Set.of("EVENT_STAFF"));
    private static final AuthenticatedActor ORGANIZER = new AuthenticatedActor(ORGANIZER_ID, Set.of("ORGANIZER"));
    private static final AuthenticatedActor OTHER_ORGANIZER = new AuthenticatedActor(UUID.randomUUID(), Set.of("ORGANIZER"));
    private static final RequestContext CONTEXT = new RequestContext("phase-4-test", null, "Bearer test-token");

    @Autowired private BookingCoordinator coordinator;
    @Autowired private BookingQueryService queryService;
    @Autowired private BookingPersistenceService persistenceService;
    @Autowired private TicketScanService scanService;
    @Autowired private MockMvc mockMvc;

    @MockitoBean private EventInventoryPort inventoryPort;

    @Autowired private CheckInAttemptRepository attemptRepository;
    @Autowired private CheckInRepository checkInRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketHoldRepository holdRepository;
    @Autowired private BookingLineItemRepository lineItemRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private BookingCommandRepository commandRepository;
    @Autowired private AttendeeProfileRepository profileRepository;
    @Autowired private AttendeeOutboxRepository outboxRepository;
    @Autowired private ProcessedIntegrationEventRepository processedRepository;

    private ExecutorService executor;

    @BeforeEach
    void cleanDatabase() {
        reset(inventoryPort);
        attemptRepository.deleteAll();
        checkInRepository.deleteAll();
        ticketRepository.deleteAll();
        holdRepository.deleteAll();
        lineItemRepository.deleteAll();
        bookingRepository.deleteAll();
        registrationRepository.deleteAll();
        commandRepository.deleteAll();
        profileRepository.deleteAll();
        outboxRepository.deleteAll();
        processedRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void duplicateBookingRequestReturnsOriginalAndOwnershipIsStrict() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        EventInventoryPort.InventoryHold hold = hold(eventId, ticketTypeId, new BigDecimal("25.00"),
                EventInventoryPort.InventoryStatus.ACTIVE, Instant.now().plusSeconds(900));
        when(inventoryPort.reserve(any(), any(), anyInt(), anyString(), any())).thenReturn(hold);

        AttendeeApi.CreateBookingRequest request = new AttendeeApi.CreateBookingRequest(eventId, ticketTypeId, 1);
        AttendeeApi.BookingResponse first = coordinator.create(request, "booking-retry-1", ATTENDEE, CONTEXT);
        AttendeeApi.BookingResponse replay = coordinator.create(request, "booking-retry-1", ATTENDEE, CONTEXT);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(first.status()).isEqualTo(BookingStatus.PAYMENT_PENDING);
        assertThat(first.hold().status()).isEqualTo(TicketHoldStatus.ACTIVE);
        assertThat(first.tickets()).isEmpty();
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(holdRepository.count()).isEqualTo(1);
        verify(inventoryPort, times(1)).reserve(any(), any(), anyInt(), anyString(), any());

        assertThatThrownBy(() -> coordinator.create(
                        new AttendeeApi.CreateBookingRequest(eventId, ticketTypeId, 2),
                        "booking-retry-1", ATTENDEE, CONTEXT))
                .isInstanceOfSatisfying(AttendeeApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        assertThatThrownBy(() -> queryService.get(first.id(), OTHER_ATTENDEE))
                .isInstanceOfSatisfying(AttendeeApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("BOOKING_OWNERSHIP_REQUIRED");
                });
    }

    @Test
    void expiredPaymentPendingHoldTransitionsBookingAndPublishesOutboxEvent() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(inventoryPort.reserve(any(), any(), anyInt(), anyString(), any())).thenReturn(
                hold(eventId, ticketTypeId, BigDecimal.TEN,
                        EventInventoryPort.InventoryStatus.ACTIVE, Instant.now().minusSeconds(1)));
        AttendeeApi.BookingResponse created = coordinator.create(
                new AttendeeApi.CreateBookingRequest(eventId, ticketTypeId, 1), "expiring-booking", ATTENDEE, CONTEXT);

        persistenceService.expireByHoldId(
                created.hold().id(), RequestContext.system("ticket-hold-expiry", created.hold().id().toString()));

        AttendeeApi.BookingResponse expired = queryService.get(created.id(), ATTENDEE);
        assertThat(expired.status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(expired.hold().status()).isEqualTo(TicketHoldStatus.EXPIRED);
        assertThat(outboxRepository.findAll())
                .extracting(com.eventplatform.attendee.outbox.AttendeeOutboxMessage::getEventType)
                .contains("event-platform.booking.created.v1", "event-platform.ticket-hold.expired.v1");
    }

    @Test
    void freeBookingIssuesSignedTicketAndRejectsTamperingWrongEventAndWrongOrganizer() {
        AttendeeApi.BookingResponse booking = createFreeBooking(UUID.randomUUID(), UUID.randomUUID(), "free-ticket");
        AttendeeApi.TicketResponse ticket = booking.tickets().get(0);

        AttendeeApi.ScanResponse valid = scanService.validate(
                new AttendeeApi.ScanRequest(booking.eventId(), ticket.qrToken()), "validate-good", STAFF, CONTEXT);
        assertThat(valid.outcome()).isEqualTo(ScanOutcome.VALID);

        String tampered = ticket.qrToken().substring(0, ticket.qrToken().length() - 1)
                + (ticket.qrToken().endsWith("A") ? "B" : "A");
        assertThat(scanService.validate(
                        new AttendeeApi.ScanRequest(booking.eventId(), tampered), "validate-tampered", STAFF, CONTEXT)
                .outcome()).isEqualTo(ScanOutcome.TAMPERED_TOKEN);
        assertThat(scanService.validate(
                        new AttendeeApi.ScanRequest(UUID.randomUUID(), ticket.qrToken()), "validate-wrong-event", STAFF, CONTEXT)
                .outcome()).isEqualTo(ScanOutcome.WRONG_EVENT);
        assertThat(scanService.validate(
                        new AttendeeApi.ScanRequest(booking.eventId(), ticket.qrToken()),
                        "validate-wrong-owner", OTHER_ORGANIZER, CONTEXT).outcome())
                .isEqualTo(ScanOutcome.ORGANIZER_NOT_OWNER);
        assertThat(scanService.validate(
                        new AttendeeApi.ScanRequest(booking.eventId(), ticket.qrToken()),
                        "validate-owner", ORGANIZER, CONTEXT).outcome())
                .isEqualTo(ScanOutcome.VALID);
        assertThat(attemptRepository.count()).isEqualTo(5);
    }

    @Test
    void concurrentCheckInProducesOneTransitionAndClearAlreadyCheckedInResult() throws Exception {
        AttendeeApi.BookingResponse booking = createFreeBooking(UUID.randomUUID(), UUID.randomUUID(), "concurrent-scan");
        String token = booking.tickets().get(0).qrToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<AttendeeApi.ScanResponse> first = executor.submit(() -> scanAtOnce(
                booking.eventId(), token, "scan-one", ready, start));
        Future<AttendeeApi.ScanResponse> second = executor.submit(() -> scanAtOnce(
                booking.eventId(), token, "scan-two", ready, start));
        ready.await();
        start.countDown();

        List<ScanOutcome> outcomes = List.of(first.get().outcome(), second.get().outcome());
        assertThat(outcomes).containsExactlyInAnyOrder(ScanOutcome.CHECKED_IN, ScanOutcome.ALREADY_CHECKED_IN);
        assertThat(checkInRepository.count()).isEqualTo(1);
        assertThat(ticketRepository.findById(booking.tickets().get(0).id()).orElseThrow().getStatus())
                .isEqualTo(com.eventplatform.attendee.domain.TicketStatus.CHECKED_IN);
        assertThat(outboxRepository.findAll().stream()
                .filter(message -> "event-platform.ticket.checked-in.v1".equals(message.getEventType())))
                .hasSize(1);

        AttendeeApi.ScanResponse replay = scanService.checkIn(
                new AttendeeApi.ScanRequest(booking.eventId(), token), "scan-one", STAFF, CONTEXT);
        assertThat(replay.outcome()).isIn(ScanOutcome.CHECKED_IN, ScanOutcome.ALREADY_CHECKED_IN);
        assertThat(replay.checkedInAt()).isNotNull();
        assertThat(attemptRepository.count()).isEqualTo(2);
    }

    @Test
    void cancelledAndRefundedTicketsAreRejectedByPhaseFiveReadyHooks() {
        AttendeeApi.BookingResponse cancelled = createFreeBooking(UUID.randomUUID(), UUID.randomUUID(), "cancelled");
        String cancelledToken = cancelled.tickets().get(0).qrToken();
        persistenceService.cancelEvent(cancelled.eventId(), RequestContext.system("event-cancelled", cancelled.eventId().toString()));
        assertThat(scanService.validate(
                        new AttendeeApi.ScanRequest(cancelled.eventId(), cancelledToken), "scan-cancelled", STAFF, CONTEXT)
                .outcome()).isEqualTo(ScanOutcome.TICKET_CANCELLED);

        AttendeeApi.BookingResponse refunded = createFreeBooking(UUID.randomUUID(), UUID.randomUUID(), "refunded");
        String refundedToken = refunded.tickets().get(0).qrToken();
        persistenceService.markRefunded(refunded.id());
        assertThat(scanService.validate(
                        new AttendeeApi.ScanRequest(refunded.eventId(), refundedToken), "scan-refunded", STAFF, CONTEXT)
                .outcome()).isEqualTo(ScanOutcome.TICKET_REFUNDED);
    }

    @Test
    void rbacAndOpenApiDocumentThePhaseFourSurface() throws Exception {
        mockMvc.perform(get("/api/v1/bookings")
                        .with(jwt().jwt(token -> token.subject(ATTENDEE_ID.toString()).claim("roles", List.of("ATTENDEE")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ATTENDEE"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings")
                        .with(jwt().jwt(token -> token.subject(ORGANIZER_ID.toString()).claim("roles", List.of("ORGANIZER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/tickets/validate")
                        .header("Idempotency-Key", "attendee-cannot-scan")
                        .contentType("application/json")
                        .content("{\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"qrToken\":\"invalid\"}")
                        .with(jwt().jwt(token -> token.subject(ATTENDEE_ID.toString())
                                        .claim("roles", List.of("ATTENDEE")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ATTENDEE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/bookings'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attendees/me/tickets'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tickets/validate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/check-ins'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    private AttendeeApi.BookingResponse createFreeBooking(UUID eventId, UUID ticketTypeId, String key) {
        EventInventoryPort.InventoryHold active = hold(
                eventId, ticketTypeId, BigDecimal.ZERO,
                EventInventoryPort.InventoryStatus.ACTIVE, Instant.now().plusSeconds(900));
        EventInventoryPort.InventoryHold confirmed = new EventInventoryPort.InventoryHold(
                active.id(), active.eventId(), active.eventOrganizerId(), active.eventTitle(),
                active.eventStartsAt(), active.eventEndsAt(), active.venueId(), active.venueSpaceId(),
                active.ticketTypeId(), active.ticketTypeName(), active.unitPrice(), active.currency(),
                active.requesterId(), active.quantity(), EventInventoryPort.InventoryStatus.CONFIRMED,
                active.expiresAt(), Instant.now(), active.remainingQuantity(), active.createdAt(), Instant.now());
        when(inventoryPort.reserve(eq(eventId), eq(ticketTypeId), eq(1), anyString(), any())).thenReturn(active);
        when(inventoryPort.confirm(eq(eventId), eq(ticketTypeId), eq(active.id()), anyString(), any())).thenReturn(confirmed);
        return coordinator.create(new AttendeeApi.CreateBookingRequest(eventId, ticketTypeId, 1), key, ATTENDEE, CONTEXT);
    }

    private EventInventoryPort.InventoryHold hold(
            UUID eventId, UUID ticketTypeId, BigDecimal price,
            EventInventoryPort.InventoryStatus status, Instant expiresAt) {
        Instant now = Instant.now();
        return new EventInventoryPort.InventoryHold(
                UUID.randomUUID(), eventId, ORGANIZER_ID, "Phase 4 Event",
                now.plusSeconds(86_400), now.plusSeconds(90_000), UUID.randomUUID(), null,
                ticketTypeId, "General admission", price, "USD", ATTENDEE_ID, 1,
                status, expiresAt, status == EventInventoryPort.InventoryStatus.CONFIRMED ? now : null,
                9, now, now);
    }

    private AttendeeApi.ScanResponse scanAtOnce(
            UUID eventId, String token, String idempotencyKey,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return scanService.checkIn(new AttendeeApi.ScanRequest(eventId, token), idempotencyKey, STAFF, CONTEXT);
    }
}
