package com.eventplatform.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventplatform.event.api.EventApi;
import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.api.RequestContext;
import com.eventplatform.event.application.EventCategoryService;
import com.eventplatform.event.application.EventManagementService;
import com.eventplatform.event.application.PublicEventQueryService;
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
class EventLifecycleIntegrationTest {

    private static final AuthenticatedActor OWNER = new AuthenticatedActor(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), Set.of("ORGANIZER"));
    private static final AuthenticatedActor OTHER = new AuthenticatedActor(
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), Set.of("ORGANIZER"));
    private static final RequestContext CONTEXT = new RequestContext("phase-3-test", null);
    private static final String BEARER = "Bearer test-token";

    @Autowired
    private EventCategoryService categoryService;

    @Autowired
    private EventManagementService eventService;

    @Autowired
    private TicketTypeService ticketService;

    @Autowired
    private PublicEventQueryService publicQueryService;

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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueAvailabilityPort venueAvailabilityPort;

    @BeforeEach
    void cleanDatabase() {
        reset(venueAvailabilityPort);
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void organizerOwnershipAndRbacAreEnforced() throws Exception {
        EventApi.CategoryResponse category = category();
        EventApi.EventResponse event = eventService.create(eventRequest(category.id(), 100), OWNER, CONTEXT);

        assertThatThrownBy(() -> eventService.update(event.id(), eventRequest(category.id(), 90), OTHER, CONTEXT))
                .isInstanceOfSatisfying(EventApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(403);
                    assertThat(exception.getCode()).isEqualTo("EVENT_OWNERSHIP_REQUIRED");
                });

        mockMvc.perform(get("/api/v1/events/{eventId}/management", event.id())
                        .with(jwt().jwt(token -> token
                                        .subject(UUID.randomUUID().toString())
                                        .claim("roles", List.of("ATTENDEE")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ATTENDEE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void openApiDocumentsLifecycleAndInventoryContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/events'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventId}/transitions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/inventory/reservations'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void publicationRequiresTicketsAndSuccessfulVenueCapacityReservation() {
        EventApi.CategoryResponse category = category();
        EventApi.EventResponse event = eventService.create(eventRequest(category.id(), 100), OWNER, CONTEXT);

        assertThatThrownBy(() -> eventService.transition(
                        event.id(), EventStatus.PUBLISHED, OWNER, CONTEXT, BEARER))
                .isInstanceOfSatisfying(EventApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("EVENT_REQUIRES_TICKET_TYPE"));

        EventApi.TicketTypeResponse ticket =
                ticketService.create(event.id(), ticketRequest("General", 100), OWNER, CONTEXT);
        when(venueAvailabilityPort.reserve(
                        any(), any(), any(), any(), anyInt(), anyString(), eq(BEARER)))
                .thenThrow(new EventApiException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "VENUE_VALIDATION_FAILED",
                        "venue too small"));

        assertThatThrownBy(() -> eventService.transition(
                        event.id(), EventStatus.PUBLISHED, OWNER, CONTEXT, BEARER))
                .isInstanceOfSatisfying(EventApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("VENUE_VALIDATION_FAILED"));
        assertThat(eventService.getManagement(event.id(), OWNER).status()).isEqualTo(EventStatus.DRAFT);

        UUID reservationId = UUID.randomUUID();
        when(venueAvailabilityPort.reserve(
                        any(), any(), any(), any(), anyInt(), anyString(), eq(BEARER)))
                .thenReturn(new VenueAvailabilityPort.Reservation(
                        reservationId,
                        event.venueId(),
                        event.venueSpaceId(),
                        100));

        EventApi.EventResponse published = eventService.transition(
                event.id(), EventStatus.PUBLISHED, OWNER, CONTEXT, BEARER);

        assertThat(published.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(published.venueReservationId()).isEqualTo(reservationId);
        assertThat(publicQueryService.detail(event.id()).id()).isEqualTo(event.id());
        assertThat(outboxRepository.findAll())
                .extracting(com.eventplatform.event.outbox.OutboxMessage::getEventType)
                .contains("event-platform.event.published.v1");
        EventApi.TicketTypeRequest paused = new EventApi.TicketTypeRequest(
                ticket.name(),
                ticket.description(),
                ticket.price(),
                ticket.currency(),
                ticket.allocation(),
                ticket.salesStart(),
                ticket.salesEnd(),
                ticket.minQuantity(),
                ticket.maxQuantity(),
                TicketTypeStatus.PAUSED);
        assertThatThrownBy(() -> ticketService.update(event.id(), ticket.id(), paused, OWNER, CONTEXT))
                .isInstanceOfSatisfying(EventApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("EVENT_REQUIRES_TICKET_TYPE"));
        verify(venueAvailabilityPort, atLeastOnce()).reserve(
                eq(event.venueId()),
                eq(event.venueSpaceId()),
                any(),
                any(),
                eq(100),
                eq("event:" + event.id()),
                eq(BEARER));
    }

    @Test
    void lifecycleTransitionsAndPublicVisibilityAreExplicit() {
        EventApi.CategoryResponse category = category();
        EventApi.EventResponse draft = eventService.create(eventRequest(category.id(), 20), OWNER, CONTEXT);
        ticketService.create(draft.id(), ticketRequest("Standard", 20), OWNER, CONTEXT);
        when(venueAvailabilityPort.reserve(any(), any(), any(), any(), anyInt(), anyString(), anyString()))
                .thenReturn(new VenueAvailabilityPort.Reservation(
                        UUID.randomUUID(), draft.venueId(), null, 20));

        assertThatThrownBy(() -> publicQueryService.detail(draft.id()))
                .isInstanceOfSatisfying(EventApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PUBLIC_EVENT_NOT_FOUND"));
        assertThatThrownBy(() -> eventService.transition(
                        draft.id(), EventStatus.SALES_OPEN, OWNER, CONTEXT, BEARER))
                .isInstanceOfSatisfying(EventApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_EVENT_TRANSITION"));

        eventService.transition(draft.id(), EventStatus.PUBLISHED, OWNER, CONTEXT, BEARER);
        EventApi.EventResponse salesOpen = eventService.transition(
                draft.id(), EventStatus.SALES_OPEN, OWNER, CONTEXT, BEARER);
        assertThat(salesOpen.status()).isEqualTo(EventStatus.SALES_OPEN);

        assertThat(publicQueryService.list(
                        category.id(), null, null, EventStatus.SALES_OPEN, "summit", 0, 10).content())
                .extracting(EventApi.PublicEventSummary::id)
                .containsExactly(draft.id());
        assertThatThrownBy(() -> eventService.transition(
                        draft.id(), EventStatus.COMPLETED, OWNER, CONTEXT, BEARER))
                .isInstanceOfSatisfying(EventApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("EVENT_NOT_ENDED"));

        EventApi.EventResponse cancelled = eventService.transition(
                draft.id(), EventStatus.CANCELLED, OWNER, CONTEXT, BEARER);
        assertThat(cancelled.status()).isEqualTo(EventStatus.CANCELLED);
        verify(venueAvailabilityPort).release(
                eq(draft.venueId()), eq(cancelled.venueReservationId()), eq(BEARER));
        assertThat(publicQueryService.detail(draft.id()).status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void ticketSalesWindowsAndCombinedAllocationRespectEventCapacity() {
        EventApi.CategoryResponse category = category();
        EventApi.EventResponse event = eventService.create(eventRequest(category.id(), 10), OWNER, CONTEXT);
        EventApi.TicketTypeRequest invalidWindow = new EventApi.TicketTypeRequest(
                "Late",
                null,
                BigDecimal.TEN,
                "usd",
                2,
                event.startsAt().minusSeconds(60),
                event.startsAt().plusSeconds(60),
                1,
                2,
                TicketTypeStatus.ACTIVE);

        assertThatThrownBy(() -> ticketService.create(event.id(), invalidWindow, OWNER, CONTEXT))
                .isInstanceOfSatisfying(EventApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("TICKET_SALES_AFTER_EVENT_START"));

        ticketService.create(event.id(), ticketRequest("First", 6), OWNER, CONTEXT);
        assertThatThrownBy(() -> ticketService.create(event.id(), ticketRequest("Second", 5), OWNER, CONTEXT))
                .isInstanceOfSatisfying(EventApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("TICKET_ALLOCATION_EXCEEDS_EVENT_CAPACITY"));
    }

    private EventApi.CategoryResponse category() {
        return categoryService.create(new EventApi.CategoryRequest(
                "technology", "Technology", "Technology events"));
    }

    private EventApi.EventRequest eventRequest(UUID categoryId, int capacity) {
        Instant startsAt = Instant.now().plusSeconds(172_800);
        return new EventApi.EventRequest(
                "Cairo Technology Summit",
                "A detailed public event description",
                categoryId,
                "Africa/Cairo",
                startsAt,
                startsAt.plusSeconds(14_400),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                null,
                capacity);
    }

    private EventApi.TicketTypeRequest ticketRequest(String name, int allocation) {
        Instant now = Instant.now();
        return new EventApi.TicketTypeRequest(
                name,
                "Admission",
                new BigDecimal("25.00"),
                "usd",
                allocation,
                now.minusSeconds(60),
                now.plusSeconds(86_400),
                1,
                Math.min(5, allocation),
                TicketTypeStatus.ACTIVE);
    }
}
