package com.seatsync.modules.booking.service;

import com.seatsync.modules.booking.dto.request.CreateBookingRequest;
import com.seatsync.modules.booking.dto.response.BookingResponse;

import java.util.List;

public interface BookingService {

    /**
     * Atomically confirms a booking within a database transaction:
     * creates PENDING booking, performs CAS update to BOOKED, inserts booking items,
     * executes mock payment, updates to CONFIRMED, inserts transactional outbox event,
     * and performs post-commit Compare-And-Delete on Redis.
     */
    BookingResponse createBooking(CreateBookingRequest request, Long authenticatedUserId);

    /**
     * Retrieves all bookings created by the authenticated customer.
     */
    List<BookingResponse> getMyBookings(Long authenticatedUserId);

    /**
     * Retrieves a booking by ID with ownership enforcement (bypassed for ADMIN).
     */
    BookingResponse getBookingById(Long bookingId, Long authenticatedUserId, boolean isAdmin);
}

