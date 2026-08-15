package com.eventplatform.event.application;

import com.eventplatform.event.api.EventApi;
import com.eventplatform.event.api.EventApiException;
import com.eventplatform.event.domain.CategoryStatus;
import com.eventplatform.event.domain.EventCategory;
import com.eventplatform.event.domain.EventCategoryRepository;
import com.eventplatform.event.domain.EventStatus;
import com.eventplatform.event.domain.ManagedEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventCategoryService {

    private final EventCategoryRepository categoryRepository;
    private final ManagedEventRepository eventRepository;
    private final PublicEventCache cache;

    public EventCategoryService(
            EventCategoryRepository categoryRepository,
            ManagedEventRepository eventRepository,
            PublicEventCache cache) {
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<EventApi.CategoryResponse> listActive() {
        return categoryRepository.findAllByStatusOrderByNameAsc(CategoryStatus.ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EventApi.CategoryResponse create(EventApi.CategoryRequest request) {
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        requireUniqueSlug(slug, null);
        Instant now = Instant.now();
        EventCategory category = new EventCategory(
                UUID.randomUUID(),
                slug,
                request.name().trim(),
                cleanNullable(request.description()),
                now);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public EventApi.CategoryResponse update(UUID categoryId, EventApi.CategoryRequest request) {
        EventCategory category = required(categoryId);
        if (category.getStatus() == CategoryStatus.ARCHIVED) {
            throw new EventApiException(HttpStatus.CONFLICT, "CATEGORY_ARCHIVED", "The event category is archived");
        }
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        requireUniqueSlug(slug, categoryId);
        category.update(slug, request.name().trim(), cleanNullable(request.description()), Instant.now());
        eventRepository.findAllByCategoryId(categoryId).forEach(event -> cache.evict(event.getId()));
        return toResponse(category);
    }

    @Transactional
    public void archive(UUID categoryId) {
        EventCategory category = required(categoryId);
        if (category.getStatus() == CategoryStatus.ARCHIVED) {
            return;
        }
        if (eventRepository.existsByCategoryIdAndStatusNot(categoryId, EventStatus.ARCHIVED)) {
            throw new EventApiException(
                    HttpStatus.CONFLICT,
                    "CATEGORY_IN_USE",
                    "Archive or recategorize events before archiving this category");
        }
        category.archive(Instant.now());
    }

    public EventCategory requiredActive(UUID categoryId) {
        EventCategory category = required(categoryId);
        if (category.getStatus() != CategoryStatus.ACTIVE) {
            throw new EventApiException(HttpStatus.CONFLICT, "CATEGORY_ARCHIVED", "The event category is archived");
        }
        return category;
    }

    public EventCategory required(UUID categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new EventApiException(
                HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "The event category was not found"));
    }

    public EventApi.CategoryResponse toResponse(EventCategory category) {
        return new EventApi.CategoryResponse(
                category.getId(),
                category.getSlug(),
                category.getName(),
                category.getDescription(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

    private void requireUniqueSlug(String slug, UUID currentId) {
        categoryRepository.findBySlugIgnoreCase(slug).ifPresent(existing -> {
            if (!existing.getId().equals(currentId)) {
                throw new EventApiException(HttpStatus.CONFLICT, "CATEGORY_SLUG_EXISTS", "Category slug already exists");
            }
        });
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
