package com.eventplatform.event.infrastructure.venue;

import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.application.VenueAvailabilityPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpVenueAvailabilityAdapter implements VenueAvailabilityPort {

    private final RestClient restClient;

    public HttpVenueAvailabilityAdapter(RestClient venueRestClient) {
        this.restClient = venueRestClient;
    }

    @Override
    public Reservation reserve(
            UUID venueId,
            UUID venueSpaceId,
            Instant startsAt,
            Instant endsAt,
            int requiredCapacity,
            String ownerReference,
            String bearerToken) {
        try {
            VenueReservationResponse response = restClient.post()
                    .uri("/api/v1/venues/{venueId}/reservations", venueId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .body(new VenueReservationRequest(
                            venueSpaceId, startsAt, endsAt, requiredCapacity, ownerReference))
                    .retrieve()
                    .body(VenueReservationResponse.class);
            if (response == null) {
                throw unavailable();
            }
            return new Reservation(response.id(), response.venueId(), response.venueSpaceId(), response.requiredCapacity());
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public void release(UUID venueId, UUID reservationId, String bearerToken) {
        try {
            restClient.delete()
                    .uri("/api/v1/venues/{venueId}/reservations/{reservationId}", venueId, reservationId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private EventApiException translate(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 403) {
            return new EventApiException(
                    HttpStatus.FORBIDDEN,
                    "VENUE_ACCESS_DENIED",
                    "The organizer cannot assign the selected venue");
        }
        if (exception.getStatusCode().value() == 404) {
            return new EventApiException(
                    HttpStatus.CONFLICT,
                    "VENUE_NOT_FOUND",
                    "The selected venue or room does not exist");
        }
        if (exception.getStatusCode().is4xxClientError()) {
            return new EventApiException(
                    HttpStatus.CONFLICT,
                    "VENUE_VALIDATION_FAILED",
                    "The selected venue cannot host this event and schedule");
        }
        return unavailable();
    }

    private EventApiException unavailable() {
        return new EventApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "VENUE_SERVICE_UNAVAILABLE",
                "Venue validation is temporarily unavailable");
    }

    private record VenueReservationRequest(
            UUID venueSpaceId,
            Instant startsAt,
            Instant endsAt,
            int requiredCapacity,
            String ownerReference) {
    }

    private record VenueReservationResponse(
            UUID id,
            UUID venueId,
            UUID venueSpaceId,
            int requiredCapacity) {
    }
}
