package com.seatsync.modules.event.controller;

import com.seatsync.common.api.ApiResponse;
import com.seatsync.common.api.PageResponse;
import com.seatsync.modules.event.dto.CreateVenueRequest;
import com.seatsync.modules.event.dto.VenueResponse;
import com.seatsync.modules.event.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VenueResponse>>> getVenues(
            @RequestParam(required = false) String city,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        PageResponse<VenueResponse> response;
        if (city != null && !city.isBlank()) {
            response = venueService.getVenuesByCity(city, pageable);
        } else {
            response = venueService.getAllVenues(pageable);
        }
        return ResponseEntity.ok(ApiResponse.success("Venues retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VenueResponse>> getVenueById(@PathVariable Long id) {
        VenueResponse venue = venueService.getVenueById(id);
        return ResponseEntity.ok(ApiResponse.success("Venue retrieved successfully", venue));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<VenueResponse>> createVenue(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse venue = venueService.createVenue(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Venue created successfully", venue));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<VenueResponse>> updateVenue(
            @PathVariable Long id,
            @Valid @RequestBody CreateVenueRequest request) {
        VenueResponse venue = venueService.updateVenue(id, request);
        return ResponseEntity.ok(ApiResponse.success("Venue updated successfully", venue));
    }
}

