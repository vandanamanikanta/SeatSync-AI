package com.seatsync.modules.booking.service;

import com.seatsync.modules.booking.dto.request.SeatHoldRequest;
import com.seatsync.modules.booking.dto.request.SeatReleaseRequest;
import com.seatsync.modules.booking.dto.response.SeatHoldResponse;
import com.seatsync.modules.booking.dto.response.SeatReleaseResponse;

public interface SeatHoldService {

    /**
     * Atomically reserves seats for 5 minutes (300 seconds) using Redis Lua script
     * followed by MySQL conditional update with pessimistic row locking.
     * Implements immediate compensation if MySQL update fails.
     */
    SeatHoldResponse holdSeats(SeatHoldRequest request, Long authenticatedUserId);

    /**
     * Releases held seats matching the provided holdToken using Compare-and-Delete (CAD).
     */
    SeatReleaseResponse releaseSeats(SeatReleaseRequest request, Long authenticatedUserId, boolean isAdmin);

    /**
     * Background reconciliation worker that safely resets expired holds in MySQL
     * under row locks and performs token-aware Compare-and-Delete (CAD) in Redis.
     */
    int reconcileExpiredHolds();
}

