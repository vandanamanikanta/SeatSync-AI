package com.seatsync.modules.booking.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Real-time seat status update payload broadcast over WebSocket / STOMP.
 * Topic: /topic/events/{eventId}/seats
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatUpdateMessage {
    private String messageId;
    private Long eventId;
    private Long seatId;
    private List<Long> seatIds;
    private String status; // "AVAILABLE", "HELD", "BOOKED"
    private Instant timestamp;
}

