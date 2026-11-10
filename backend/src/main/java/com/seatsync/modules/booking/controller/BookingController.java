package com.seatsync.modules.booking.controller;

import com.seatsync.common.api.ApiResponse;
import com.seatsync.common.security.SecurityUtils;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.booking.dto.request.CreateBookingRequest;
import com.seatsync.modules.booking.dto.response.BookingResponse;
import com.seatsync.modules.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller exposing booking checkout, customer booking history, and detail endpoints.
 * Restricted to authenticated CUSTOMER and ADMIN roles; ORGANIZER users are rejected.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        BookingResponse response = bookingService.createBooking(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking created and confirmed successfully", response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<BookingResponse> responses = bookingService.getMyBookings(userId);
        return ResponseEntity.ok(ApiResponse.success("User bookings retrieved successfully", responses));
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingById(@PathVariable Long bookingId) {
        UserPrincipal principal = SecurityUtils.getRequiredCurrentUserPrincipal();
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        BookingResponse response = bookingService.getBookingById(bookingId, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Booking details retrieved successfully", response));
    }
}

