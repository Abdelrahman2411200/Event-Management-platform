package com.eventplatform.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.eventplatform.event.api.EventApi;
import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.api.RequestContext;
import com.eventplatform.event.application.EventCategoryService;
import com.eventplatform.event.application.EventManagementService;
import com.eventplatform.event.application.InventoryService;
import com.eventplatform.event.application.TicketTypeService;
import com.eventplatform.event.application.VenueAvailabilityPort;
import com.eventplatform.event.domain.EventCategoryRepository;
import com.eventplatform.event.domain.EventStatus;
import com.eventplatform.event.domain.InventoryReservationRepository;
import com.eventplatform.event.domain.InventoryReservationStatus;
import com.eventplatform.event.domain.ManagedEventRepository;
import com.eventplatform.event.domain.TicketTypeRepository;
import com.eventplatform.event.domain.TicketTypeStatus;
import com.eventplatform.event.outbox.OutboxMessageRepository;
import com.eventplatform.event.security.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class InventoryHoldLifecycleIntegrationTest {
    private static final AuthenticatedActor ORGANIZER = new AuthenticatedActor(UUID.randomUUID(), Set.of("ORGANIZER"));
    private static final AuthenticatedActor ATTENDEE = new AuthenticatedActor(UUID.randomUUID(), Set.of("ATTENDEE"));
    private static final RequestContext CONTEXT = new RequestContext("phase-4-inventory", null);
    private static final String BEARER = "Bearer test-token";

    @Autowired private EventCategoryService categoryService;
    @Autowired private EventManagementService eventService;
    @Autowired private TicketTypeService ticketTypeService;
    @Autowired private InventoryService inventoryService;
    @Autowired private OutboxMessageRepository outboxRepository;
    @Autowired private InventoryReservationRepository reservationRepository;
    @Autowired private TicketTypeRepository ticketRepository;
    @Autowired private ManagedEventRepository eventRepository;
    @Autowired private EventCategoryRepository categoryRepository;

    @MockitoBean private VenueAvailabilityPort venueAvailabilityPort;
    @MockitoBean private Clock clock;

    private Instant now;

    @BeforeEach
    void setUp() {
        reset(venueAvailabilityPort, clock);
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        categoryRepository.deleteAll();
        now = Instant.now();
        when(clock.instant()).thenReturn(now);
    }

    @Test
    void expiredLastTicketHoldRestoresInventoryAndPublishesExpiryThroughOutbox() {
        Fixture fixture = fixture();
        EventApi.InventoryReservationResponse held = inventoryService.reserve(
                fixture.eventId(), fixture.ticketTypeId(), 1, "phase4-expire", ATTENDEE, CONTEXT);
        assertThat(held.remainingQuantity()).isZero();

        when(clock.instant()).thenReturn(held.expiresAt().plusSeconds(1));
        inventoryService.expireReservation(held.id());

        assertThat(reservationRepository.findById(held.id()).orElseThrow().getStatus())
                .isEqualTo(InventoryReservationStatus.EXPIRED);
        assertThat(inventoryService.availability(fixture.eventId(), fixture.ticketTypeId()).availableQuantity())
                .isEqualTo(1);
        assertThat(outboxRepository.findAll())
                .extracting(com.eventplatform.event.outbox.OutboxMessage::getEventType)
                .contains("event-platform.inventory.held.v1", "event-platform.inventory.expired.v1");
    }

    @Test
    void confirmedHoldIsIdempotentAndNeverReturnedByExpiryWorker() {
        Fixture fixture = fixture();
        EventApi.InventoryReservationResponse held = inventoryService.reserve(
                fixture.eventId(), fixture.ticketTypeId(), 1, "phase4-confirm-reserve", ATTENDEE, CONTEXT);
        EventApi.InventoryReservationResponse confirmed = inventoryService.confirm(
                fixture.eventId(), fixture.ticketTypeId(), held.id(), "phase4-confirm", ATTENDEE, CONTEXT);
        EventApi.InventoryReservationResponse replay = inventoryService.confirm(
                fixture.eventId(), fixture.ticketTypeId(), held.id(), "phase4-confirm", ATTENDEE, CONTEXT);

        assertThat(replay.id()).isEqualTo(confirmed.id());
        assertThat(replay.status()).isEqualTo(InventoryReservationStatus.CONFIRMED);
        when(clock.instant()).thenReturn(held.expiresAt().plusSeconds(1));
        inventoryService.expireReservation(held.id());
        assertThat(reservationRepository.findById(held.id()).orElseThrow().getStatus())
                .isEqualTo(InventoryReservationStatus.CONFIRMED);
        assertThat(inventoryService.availability(fixture.eventId(), fixture.ticketTypeId()).availableQuantity())
                .isZero();
        assertThat(outboxRepository.findAll().stream()
                .filter(message -> "event-platform.inventory.confirmed.v1".equals(message.getEventType())))
                .hasSize(1);

        eventService.transition(fixture.eventId(), EventStatus.CANCELLED, ORGANIZER, CONTEXT, BEARER);
        EventApi.InventoryReservationResponse reserveReplay = inventoryService.reserve(
                fixture.eventId(), fixture.ticketTypeId(), 1, "phase4-confirm-reserve", ATTENDEE, CONTEXT);
        assertThat(reserveReplay.id()).isEqualTo(held.id());
        assertThat(reserveReplay.status()).isEqualTo(InventoryReservationStatus.CONFIRMED);
    }

    @Test
    void sagaConfirmationIsIdempotentAndExpiredHoldProducesCompensatableRejection() {
        Fixture fixture = fixture();
        EventApi.InventoryReservationResponse held = inventoryService.reserve(
                fixture.eventId(), fixture.ticketTypeId(), 1, "saga-confirm-reserve", ATTENDEE, CONTEXT);
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        inventoryService.confirmSaga(bookingId, paymentId, ATTENDEE.userId(), fixture.eventId(),
                fixture.ticketTypeId(), held.id(), "confirm:" + bookingId, CONTEXT);
        inventoryService.confirmSaga(bookingId, paymentId, ATTENDEE.userId(), fixture.eventId(),
                fixture.ticketTypeId(), held.id(), "confirm:" + bookingId, CONTEXT);
        assertThat(reservationRepository.findById(held.id()).orElseThrow().getStatus())
                .isEqualTo(InventoryReservationStatus.CONFIRMED);

        setUp();
        fixture = fixture();
        held = inventoryService.reserve(
                fixture.eventId(), fixture.ticketTypeId(), 1, "saga-expired-reserve", ATTENDEE, CONTEXT);
        when(clock.instant()).thenReturn(held.expiresAt().plusSeconds(1));
        inventoryService.confirmSaga(UUID.randomUUID(), UUID.randomUUID(), ATTENDEE.userId(),
                fixture.eventId(), fixture.ticketTypeId(), held.id(), "confirm:expired", CONTEXT);
        assertThat(outboxRepository.findAll()).extracting(
                com.eventplatform.event.outbox.OutboxMessage::getEventType)
                .contains("event-platform.inventory.expired.v1",
                        "event-platform.inventory.confirmation-rejected.v1");
    }

    private Fixture fixture() {
        EventApi.CategoryResponse category = categoryService.create(
                new EventApi.CategoryRequest("phase-four", "Phase Four", null));
        Instant startsAt = Instant.now().plusSeconds(172_800);
        EventApi.EventResponse event = eventService.create(
                new EventApi.EventRequest(
                        "Phase 4 inventory", "Inventory lifecycle", category.id(), "UTC",
                        startsAt, startsAt.plusSeconds(7_200), UUID.randomUUID(), null, 1),
                ORGANIZER, CONTEXT);
        EventApi.TicketTypeResponse ticket = ticketTypeService.create(
                event.id(),
                new EventApi.TicketTypeRequest(
                        "Last ticket", null, BigDecimal.ZERO, "USD", 1,
                        Instant.now().minusSeconds(60), Instant.now().plusSeconds(86_400),
                        1, 1, TicketTypeStatus.ACTIVE),
                ORGANIZER, CONTEXT);
        when(venueAvailabilityPort.reserve(any(), any(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(new VenueAvailabilityPort.Reservation(UUID.randomUUID(), event.venueId(), null, 1));
        eventService.transition(event.id(), EventStatus.PUBLISHED, ORGANIZER, CONTEXT, BEARER);
        eventService.transition(event.id(), EventStatus.SALES_OPEN, ORGANIZER, CONTEXT, BEARER);
        return new Fixture(event.id(), ticket.id());
    }

    private record Fixture(UUID eventId, UUID ticketTypeId) {
    }
}
