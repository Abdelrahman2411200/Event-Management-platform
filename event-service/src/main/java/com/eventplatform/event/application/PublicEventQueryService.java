package com.eventplatform.event.application;

import com.eventplatform.event.api.EventApi;
import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.domain.EventStatus;
import com.eventplatform.event.domain.ManagedEvent;
import com.eventplatform.event.domain.ManagedEventRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicEventQueryService {

    private final ManagedEventRepository eventRepository;
    private final EventResponseMapper mapper;

    public PublicEventQueryService(
            ManagedEventRepository eventRepository,
            EventResponseMapper mapper) {
        this.eventRepository = eventRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public EventApi.PageResponse<EventApi.PublicEventSummary> list(
            UUID categoryId,
            Instant startsAfter,
            Instant startsBefore,
            EventStatus status,
            String search,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION",
                    "Page must be non-negative and size must be between 1 and 100");
        }
        if (startsAfter != null && startsBefore != null && startsBefore.isBefore(startsAfter)) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_FILTER",
                    "startsBefore must be after startsAfter");
        }
        Set<EventStatus> visible = EventStatus.publicStatuses();
        if (status != null && !status.isPublic()) {
            throw new EventApiException(
                    HttpStatus.BAD_REQUEST,
                    "NON_PUBLIC_EVENT_STATUS",
                    "Public discovery cannot filter by a non-public status");
        }
        Specification<ManagedEvent> specification = (root, query, builder) -> status == null
                ? root.get("status").in(visible)
                : builder.equal(root.get("status"), status);
        if (categoryId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("categoryId"), categoryId));
        }
        if (startsAfter != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("startsAt"), startsAfter));
        }
        if (startsBefore != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("startsAt"), startsBefore));
        }
        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("title")), term),
                    builder.like(builder.lower(root.get("description")), term)));
        }
        Page<ManagedEvent> result = eventRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startsAt")));
        return new EventApi.PageResponse<>(
                result.getContent().stream().map(mapper::summary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PublicEventCache.DETAILS, key = "#eventId")
    public EventApi.PublicEventDetail detail(UUID eventId) {
        ManagedEvent event = eventRepository.findByIdAndStatusIn(eventId, EventStatus.publicStatuses())
                .orElseThrow(() -> new EventApiException(
                        HttpStatus.NOT_FOUND,
                        "PUBLIC_EVENT_NOT_FOUND",
                        "The published event was not found"));
        return mapper.publicDetail(event);
    }
}
