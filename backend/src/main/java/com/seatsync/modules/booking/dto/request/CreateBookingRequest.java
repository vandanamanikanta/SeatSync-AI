package com.seatsync.modules.booking.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
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
 * Request payload for confirming booking checkout.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @JsonAlias({"seatIds", "seats"})
    @NotEmpty(message = "At least one seat ID is required")
    @Size(max = 10, message = "Cannot book more than 10 seats in a single reservation")
    private List<Long> eventSeatIds;

    @NotBlank(message = "Hold token is required")
    private String holdToken;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @Builder.Default
    private String paymentMethod = "MOCK_CREDIT_CARD";

    public List<Long> getResolvedSeatIds() {
        if (eventSeatIds == null || eventSeatIds.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(eventSeatIds);
    }
}

