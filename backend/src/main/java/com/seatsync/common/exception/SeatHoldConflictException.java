package com.seatsync.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when seat hold acquisition fails due to seats being already held, booked, or conflicting.
 */
@Getter
public class SeatHoldConflictException extends ApiException {

    private final List<Long> unavailableSeatIds;

    public SeatHoldConflictException(String message, List<Long> unavailableSeatIds) {
        super(message, HttpStatus.CONFLICT, "SEATS_ALREADY_RESERVED");
        this.unavailableSeatIds = unavailableSeatIds != null ? unavailableSeatIds : Collections.emptyList();
    }

    public SeatHoldConflictException(String message) {
        this(message, Collections.emptyList());
    }
}

