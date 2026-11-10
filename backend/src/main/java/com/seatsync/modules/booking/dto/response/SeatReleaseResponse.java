package com.seatsync.modules.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload confirming release of seat holds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatReleaseResponse {

    private Long eventId;
    private List<Long> releasedSeatIds;
    private int releasedCount;
}

