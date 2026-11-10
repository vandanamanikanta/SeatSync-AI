package com.seatsync.modules.booking.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Request payload for releasing held seats manually.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatReleaseRequest {

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotBlank(message = "Hold token is required")
    private String holdToken;

    @JsonAlias({"seatIds", "seats"})
    private List<Long> eventSeatIds;

    public List<Long> getResolvedSeatIds() {
        if (eventSeatIds == null || eventSeatIds.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(eventSeatIds);
    }
}

