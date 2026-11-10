package com.seatsync.modules.event.controller;

import com.seatsync.common.api.ApiResponse;
import com.seatsync.common.api.PageResponse;
import com.seatsync.common.security.SecurityUtils;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.event.dto.CreateEventRequest;
import com.seatsync.modules.event.dto.EventResponse;
import com.seatsync.modules.event.dto.EventSearchCriteria;
import com.seatsync.modules.event.dto.UpdateEventRequest;
import com.seatsync.modules.event.entity.EventStatus;
import com.seatsync.modules.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EventResponse>>> searchEvents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) EventStatus status,
            @PageableDefault(size = 12, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable) {

        // If unauthenticated or customer, default to showing only PUBLISHED events
        Optional<UserPrincipal> currentUser = SecurityUtils.getCurrentUserPrincipal();
        boolean isStaff = currentUser.isPresent() && currentUser.get().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_ORGANIZER"));

        EventStatus effectiveStatus = status;
        if (!isStaff && effectiveStatus == null) {
            effectiveStatus = EventStatus.PUBLISHED;
        }

        EventSearchCriteria criteria = EventSearchCriteria.builder()
                .search(search)
                .category(category)
                .city(city)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .startDate(startDate)
                .endDate(endDate)
                .status(effectiveStatus)
                .build();

        PageResponse<EventResponse> response = eventService.searchEvents(criteria, pageable);
        return ResponseEntity.ok(ApiResponse.success("Events retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getEventById(@PathVariable Long id) {
        EventResponse event = eventService.getEventById(id);
        return ResponseEntity.ok(ApiResponse.success("Event details retrieved successfully", event));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<EventResponse>> getEventBySlug(@PathVariable String slug) {
        EventResponse event = eventService.getEventBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success("Event details retrieved successfully", event));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<EventResponse>>> getMyEvents(
            @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        PageResponse<EventResponse> response = eventService.getEventsByOrganizer(currentUserId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Organizer events retrieved successfully", response));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(@Valid @RequestBody CreateEventRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        EventResponse event = eventService.createEvent(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Event created successfully", event));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.getRequiredCurrentUserPrincipal().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        EventResponse updated = eventService.updateEvent(id, request, currentUserId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Event updated successfully", updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<EventResponse>> updateEventStatus(
            @PathVariable Long id,
            @RequestParam EventStatus status) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.getRequiredCurrentUserPrincipal().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        EventResponse updated = eventService.updateEventStatus(id, status, currentUserId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Event status updated to " + status, updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.getRequiredCurrentUserPrincipal().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        eventService.deleteEvent(id, currentUserId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Event deleted successfully"));
    }
}

