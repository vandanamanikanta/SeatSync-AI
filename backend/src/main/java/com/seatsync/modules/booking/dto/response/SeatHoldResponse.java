package com.seatsync.modules.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response payload confirming successful seat hold with token and expiration time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldResponse {

    private String holdToken;
    private Long eventId;
    private List<Long> heldSeatIds;
    private Instant expiresAt;
    private int ttlSecondsRemaining;
}

