package com.eventplatform.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventplatform.venue.api.VenueApi;
import com.eventplatform.venue.api.VenueApiException;
import com.eventplatform.venue.application.VenueAvailabilityService;
import com.eventplatform.venue.application.VenueManagementService;
import com.eventplatform.venue.domain.AvailabilityKind;
import com.eventplatform.venue.domain.VenueAvailabilityRepository;
import com.eventplatform.venue.domain.VenueRepository;
import com.eventplatform.venue.domain.VenueSpaceRepository;
import com.eventplatform.venue.security.AuthenticatedActor;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class VenueManagementIntegrationTest {

    private static final AuthenticatedActor OWNER = new AuthenticatedActor(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), Set.of("ORGANIZER"));
    private static final AuthenticatedActor OTHER = new AuthenticatedActor(
            UUID.fromString("22222222-2222-2222-2222-222222222222"), Set.of("ORGANIZER"));
    private static final AuthenticatedActor ADMIN = new AuthenticatedActor(
            UUID.fromString("33333333-3333-3333-3333-333333333333"), Set.of("ADMIN"));

    @Autowired
    private VenueManagementService venueService;

    @Autowired
    private VenueAvailabilityService availabilityService;

    @Autowired
    private VenueAvailabilityRepository availabilityRepository;

    @Autowired
    private VenueSpaceRepository spaceRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
        availabilityRepository.deleteAll();
        spaceRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void organizerOwnershipAndRoleBoundaryAreEnforced() throws Exception {
        VenueApi.VenueResponse venue = venueService.create(venueRequest(200), OWNER);

        assertThatThrownBy(() -> venueService.update(venue.id(), updateRequest(180), OTHER))
                .isInstanceOfSatisfying(VenueApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(403);
                    assertThat(exception.getCode()).isEqualTo("VENUE_OWNERSHIP_REQUIRED");
                });

        VenueApi.VenueResponse updated = venueService.update(venue.id(), updateRequest(180), ADMIN);
        assertThat(updated.totalCapacity()).isEqualTo(180);

        mockMvc.perform(get("/api/v1/venues/{venueId}", venue.id())
                        .with(jwt().jwt(token -> token
                                        .subject(UUID.randomUUID().toString())
                                        .claim("roles", java.util.List.of("ATTENDEE")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ATTENDEE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void openApiDocumentsVenueAndAvailabilityContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/venues'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/venues/{venueId}/availability/check'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void roomCapacityCannotExceedVenueCapacity() {
        VenueApi.VenueResponse venue = venueService.create(venueRequest(100), OWNER);

        assertThatThrownBy(() -> venueService.createSpace(
                        venue.id(),
                        new VenueApi.VenueSpaceRequest("Auditorium", null, 101, Set.of()),
                        OWNER))
                .isInstanceOfSatisfying(VenueApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SPACE_CAPACITY_EXCEEDS_VENUE"));
    }

    @Test
    void overlappingRoomAndWholeVenueAssignmentsAreRejected() {
        VenueApi.VenueResponse venue = venueService.create(venueRequest(300), OWNER);
        VenueApi.VenueSpaceResponse room = venueService.createSpace(
                venue.id(), new VenueApi.VenueSpaceRequest("Hall A", null, 120, Set.of("stage")), OWNER);
        Instant startsAt = Instant.now().plusSeconds(3_600);
        Instant endsAt = startsAt.plusSeconds(7_200);

        VenueApi.AvailabilityEntryResponse first = availabilityService.reserve(
                venue.id(),
                new VenueApi.AvailabilityReservationRequest(
                        room.id(), startsAt, endsAt, 100, "event:first"),
                OWNER);

        VenueApi.AvailabilityCheckResponse check = availabilityService.check(
                venue.id(),
                new VenueApi.AvailabilityCheckRequest(room.id(), startsAt.plusSeconds(60), endsAt, 80));
        assertThat(check.available()).isFalse();
        assertThat(check.conflicts()).extracting(VenueApi.AvailabilityConflictResponse::id).contains(first.id());

        assertThatThrownBy(() -> availabilityService.reserve(
                        venue.id(),
                        new VenueApi.AvailabilityReservationRequest(
                                null, startsAt.plusSeconds(60), endsAt, 200, "event:second"),
                        OWNER))
                .isInstanceOfSatisfying(VenueApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("VENUE_UNAVAILABLE"));

        VenueApi.AvailabilityEntryResponse idempotent = availabilityService.reserve(
                venue.id(),
                new VenueApi.AvailabilityReservationRequest(
                        room.id(), startsAt, endsAt, 100, "event:first"),
                OWNER);
        assertThat(idempotent.id()).isEqualTo(first.id());

        assertThatThrownBy(() -> venueService.updateSpace(
                        venue.id(),
                        room.id(),
                        new VenueApi.VenueSpaceRequest("Hall A", null, 99, Set.of("stage")),
                        OWNER))
                .isInstanceOfSatisfying(VenueApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("VENUE_SPACE_CAPACITY_BELOW_RESERVATION"));

        availabilityService.release(venue.id(), first.id(), OWNER, AvailabilityKind.EVENT_RESERVATION);
        assertThat(availabilityService.check(
                        venue.id(),
                        new VenueApi.AvailabilityCheckRequest(room.id(), startsAt, endsAt, 100)).available())
                .isTrue();

        availabilityService.reserve(
                venue.id(),
                new VenueApi.AvailabilityReservationRequest(
                        null, startsAt, endsAt, 250, "event:whole"),
                OWNER);
        assertThatThrownBy(() -> venueService.update(venue.id(), updateRequest(200), OWNER))
                .isInstanceOfSatisfying(VenueApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("VENUE_CAPACITY_BELOW_RESERVATION"));
    }

    private VenueApi.CreateVenueRequest venueRequest(int capacity) {
        return new VenueApi.CreateVenueRequest(
                "Cairo Convention Centre",
                "Main venue",
                new VenueApi.AddressRequest(
                        "1 Nile Street", null, "Cairo", "Cairo", "11511", "EG", null, null),
                "Africa/Cairo",
                capacity,
                Set.of("wifi", "parking"),
                Map.of("accessibility", "step-free"));
    }

    private VenueApi.UpdateVenueRequest updateRequest(int capacity) {
        VenueApi.CreateVenueRequest request = venueRequest(capacity);
        return new VenueApi.UpdateVenueRequest(
                request.name(),
                request.description(),
                request.address(),
                request.timezone(),
                request.totalCapacity(),
                request.amenities(),
                request.metadata());
    }
}
