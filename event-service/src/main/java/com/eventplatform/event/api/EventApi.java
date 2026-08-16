package com.eventplatform.event.api;

import com.eventplatform.event.domain.CategoryStatus;
import com.eventplatform.event.domain.EventStatus;
import com.eventplatform.event.domain.InventoryReservationStatus;
import com.eventplatform.event.domain.TicketTypeStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventApi {

    private EventApi() {
    }

    public record CategoryRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 80) String slug,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description) {
    }

    public record CategoryResponse(
            UUID id,
            String slug,
            String name,
            String description,
            CategoryStatus status,
            Instant createdAt,
            Instant updatedAt) implements Serializable {
    }

    public record EventRequest(
            @NotBlank @Size(max = 240) String title,
            @NotBlank @Size(max = 10000) String description,
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 64) String timezone,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull UUID venueId,
            UUID venueSpaceId,
            @Min(1) @Max(10_000_000) int capacity) {
    }

    public record EventTransitionRequest(@NotNull EventStatus targetStatus) {
    }

    public record TicketTypeRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @Min(1) @Max(10_000_000) int allocation,
            @NotNull Instant salesStart,
            @NotNull Instant salesEnd,
            @Min(1) @Max(100) int minQuantity,
            @Min(1) @Max(100) int maxQuantity,
            @NotNull TicketTypeStatus status) {
    }

    public record TicketTypeResponse(
            UUID id,
            UUID eventId,
            String name,
            String description,
            BigDecimal price,
            String currency,
            int allocation,
            int reservedQuantity,
            int availableQuantity,
            Instant salesStart,
            Instant salesEnd,
            int minQuantity,
            int maxQuantity,
            TicketTypeStatus status,
            Instant createdAt,
            Instant updatedAt) implements Serializable {
    }

    public record EventResponse(
            UUID id,
            UUID organizerId,
            String title,
            String description,
            CategoryResponse category,
            String timezone,
            Instant startsAt,
            Instant endsAt,
            UUID venueId,
            UUID venueSpaceId,
            UUID venueReservationId,
            int capacity,
            EventStatus status,
            List<TicketTypeResponse> ticketTypes,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PublicTicketTypeResponse(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            String currency,
            int allocation,
            int availableQuantity,
            Instant salesStart,
            Instant salesEnd,
            int minQuantity,
            int maxQuantity,
            TicketTypeStatus status,
            boolean onSale) implements Serializable {
    }

    public record PublicEventSummary(
            UUID id,
            String title,
            CategoryResponse category,
            String timezone,
            Instant startsAt,
            Instant endsAt,
            UUID venueId,
            UUID venueSpaceId,
            int capacity,
            EventStatus status) implements Serializable {
    }

    public record PublicEventDetail(
            UUID id,
            String title,
            String description,
            CategoryResponse category,
            String timezone,
            Instant startsAt,
            Instant endsAt,
            UUID venueId,
            UUID venueSpaceId,
            int capacity,
            EventStatus status,
            List<PublicTicketTypeResponse> ticketTypes,
            Instant publishedAt) implements Serializable {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) implements Serializable {
    }

    public record InventoryAvailabilityResponse(
            UUID eventId,
            UUID ticketTypeId,
            int allocation,
            int reservedQuantity,
            int availableQuantity,
            boolean onSale,
            Instant salesStart,
            Instant salesEnd) {
    }

    public record ReserveInventoryRequest(@Min(1) @Max(100) int quantity) {
    }

    public record InventoryReservationResponse(
            UUID id,
            UUID eventId,
            UUID eventOrganizerId,
            String eventTitle,
            Instant eventStartsAt,
            Instant eventEndsAt,
            UUID venueId,
            UUID venueSpaceId,
            UUID ticketTypeId,
            String ticketTypeName,
            BigDecimal unitPrice,
            String currency,
            UUID requesterId,
            int quantity,
            InventoryReservationStatus status,
            Instant expiresAt,
            Instant confirmedAt,
            int remainingQuantity,
            Instant createdAt,
            Instant updatedAt) {
    }
}
