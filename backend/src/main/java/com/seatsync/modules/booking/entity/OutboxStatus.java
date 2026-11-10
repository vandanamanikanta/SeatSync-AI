package com.seatsync.modules.booking.entity;

/**
 * Lifecycle status of an event in the transactional outbox table.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}

