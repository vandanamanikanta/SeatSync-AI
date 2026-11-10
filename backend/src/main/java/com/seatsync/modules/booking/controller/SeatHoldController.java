package com.seatsync.modules.booking.controller;

import com.seatsync.common.api.ApiResponse;
import com.seatsync.common.security.SecurityUtils;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.booking.dto.request.SeatHoldRequest;
import com.seatsync.modules.booking.dto.request.SeatReleaseRequest;
import com.seatsync.modules.booking.dto.response.SeatHoldResponse;
import com.seatsync.modules.booking.dto.response.SeatReleaseResponse;
import com.seatsync.modules.booking.service.SeatHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for high-concurrency seat hold reservations and manual release.
 * Restricted to CUSTOMER and ADMIN roles; ORGANIZER users are rejected.
 */
@RestController
@RequestMapping("/api/bookings/hold")
@RequiredArgsConstructor
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SeatHoldResponse>> holdSeats(@Valid @RequestBody SeatHoldRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        SeatHoldResponse response = seatHoldService.holdSeats(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Seats locked for 5 minutes", response));
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SeatReleaseResponse>> releaseSeats(@Valid @RequestBody SeatReleaseRequest request) {
        UserPrincipal principal = SecurityUtils.getRequiredCurrentUserPrincipal();
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        SeatReleaseResponse response = seatHoldService.releaseSeats(request, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Seats released successfully", response));
    }
}

