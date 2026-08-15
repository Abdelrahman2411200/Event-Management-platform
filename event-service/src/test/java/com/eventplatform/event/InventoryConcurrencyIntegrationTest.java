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
import com.eventplatform.event.domain.ManagedEventRepository;
import com.eventplatform.event.domain.TicketTypeRepository;
import com.eventplatform.event.domain.TicketTypeStatus;
import com.eventplatform.event.outbox.OutboxMessageRepository;
import com.eventplatform.event.security.AuthenticatedActor;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class InventoryConcurrencyIntegrationTest {

    private static final AuthenticatedActor ORGANIZER = new AuthenticatedActor(
            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), Set.of("ORGANIZER"));
    private static final RequestContext CONTEXT = new RequestContext("inventory-concurrency", null);
    private static final String BEARER = "Bearer test-token";

    @Autowired
    private EventCategoryService categoryService;

    @Autowired
    private EventManagementService eventService;

    @Autowired
    private TicketTypeService ticketTypeService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private TicketTypeRepository ticketRepository;

    @Autowired
    private ManagedEventRepository eventRepository;

    @Autowired
    private EventCategoryRepository categoryRepository;

    @MockitoBean
    private VenueAvailabilityPort venueAvailabilityPort;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        reset(venueAvailabilityPort);
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        categoryRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void shutDown() {
        executor.shutdownNow();
    }

    @Test
    void concurrentReservationsCannotOversellAndSuccessfulRetryIsIdempotent() throws Exception {
        EventApi.CategoryResponse category = categoryService.create(
                new EventApi.CategoryRequest("music", "Music", null));
        Instant startsAt = Instant.now().plusSeconds(172_800);
        EventApi.EventResponse event = eventService.create(
                new EventApi.EventRequest(
                        "Capacity Test",
                        "Concurrent inventory validation",
                        category.id(),
                        "UTC",
                        startsAt,
                        startsAt.plusSeconds(7_200),
                        UUID.randomUUID(),
                        null,
                        5),
                ORGANIZER,
                CONTEXT);
        EventApi.TicketTypeResponse ticket = ticketTypeService.create(
                event.id(),
                new EventApi.TicketTypeRequest(
                        "General",
                        null,
                        new BigDecimal("10.00"),
                        "USD",
                        5,
                        Instant.now().minusSeconds(60),
                        Instant.now().plusSeconds(86_400),
                        1,
                        5,
                        TicketTypeStatus.ACTIVE),
                ORGANIZER,
                CONTEXT);
        when(venueAvailabilityPort.reserve(any(), any(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(new VenueAvailabilityPort.Reservation(
                        UUID.randomUUID(), event.venueId(), null, 5));
        eventService.transition(event.id(), EventStatus.PUBLISHED, ORGANIZER, CONTEXT, BEARER);
        eventService.transition(event.id(), EventStatus.SALES_OPEN, ORGANIZER, CONTEXT, BEARER);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AuthenticatedActor firstActor = new AuthenticatedActor(UUID.randomUUID(), Set.of("ATTENDEE"));
        AuthenticatedActor secondActor = new AuthenticatedActor(UUID.randomUUID(), Set.of("ATTENDEE"));
        Future<Outcome> first = executor.submit(() -> reserveAtOnce(
                event.id(), ticket.id(), "reserve:first", firstActor, ready, start));
        Future<Outcome> second = executor.submit(() -> reserveAtOnce(
                event.id(), ticket.id(), "reserve:second", secondActor, ready, start));
        ready.await();
        start.countDown();

        List<Outcome> outcomes = List.of(first.get(), second.get());
        List<Outcome> successes = outcomes.stream().filter(outcome -> outcome.response() != null).toList();
        List<Outcome> failures = outcomes.stream().filter(outcome -> outcome.exception() != null).toList();
        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).exception().getCode()).isEqualTo("INSUFFICIENT_TICKET_INVENTORY");

        EventApi.InventoryAvailabilityResponse availability = inventoryService.availability(event.id(), ticket.id());
        assertThat(availability.reservedQuantity()).isEqualTo(4);
        assertThat(availability.availableQuantity()).isEqualTo(1);

        Outcome winner = successes.get(0);
        EventApi.InventoryReservationResponse replay = inventoryService.reserve(
                event.id(), ticket.id(), 4, winner.key(), winner.actor());
        assertThat(replay.id()).isEqualTo(winner.response().id());
        assertThat(replay.remainingQuantity()).isEqualTo(1);

        EventApi.InventoryReservationResponse released = inventoryService.release(
                event.id(), ticket.id(), replay.id(), "release:winner", winner.actor());
        assertThat(released.status().name()).isEqualTo("RELEASED");
        assertThat(released.remainingQuantity()).isEqualTo(5);
    }

    private Outcome reserveAtOnce(
            UUID eventId,
            UUID ticketTypeId,
            String key,
            AuthenticatedActor actor,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return new Outcome(key, actor, inventoryService.reserve(eventId, ticketTypeId, 4, key, actor), null);
        } catch (EventApiException exception) {
            return new Outcome(key, actor, null, exception);
        }
    }

    private record Outcome(
            String key,
            AuthenticatedActor actor,
            EventApi.InventoryReservationResponse response,
            EventApiException exception) {
    }
}
