package com.eventplatform.event.api;

import com.eventplatform.event.application.EventCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/event-categories")
public class EventCategoryController {

    private final EventCategoryService categoryService;

    public EventCategoryController(EventCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List active public event categories")
    public List<EventApi.CategoryResponse> list() {
        return categoryService.listActive();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create an event category (ADMIN)")
    public ResponseEntity<EventApi.CategoryResponse> create(
            @Valid @RequestBody EventApi.CategoryRequest request) {
        EventApi.CategoryResponse response = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/event-categories/" + response.id())).body(response);
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update an event category (ADMIN)")
    public EventApi.CategoryResponse update(
            @PathVariable UUID categoryId,
            @Valid @RequestBody EventApi.CategoryRequest request) {
        return categoryService.update(categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Archive an unused event category (ADMIN)")
    public ResponseEntity<Void> archive(@PathVariable UUID categoryId) {
        categoryService.archive(categoryId);
        return ResponseEntity.noContent().build();
    }
}
