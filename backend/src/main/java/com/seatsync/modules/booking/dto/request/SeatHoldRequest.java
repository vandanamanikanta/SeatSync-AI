package com.seatsync.modules.booking.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Request payload for temporary multi-seat hold acquisition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldRequest {

    @NotNull(message = "Event ID is required")
    private Long eventId;

    /**
     * Supports both 'eventSeatIds' (preferred) and 'seatIds' aliases.
     */
    @JsonAlias({"seatIds", "seats"})
    @Size(max = 10, message = "Cannot hold more than 10 seats in a single reservation")
    private List<Long> eventSeatIds;

    private String idempotencyKey;

    public List<Long> getResolvedSeatIds() {
        if (eventSeatIds == null || eventSeatIds.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(eventSeatIds);
    }
}

