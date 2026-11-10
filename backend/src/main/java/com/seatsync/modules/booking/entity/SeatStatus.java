package com.seatsync.modules.booking.entity;

/**
 * State of an event seat in the inventory lifecycle.
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    BOOKED,
    BLOCKED
}

