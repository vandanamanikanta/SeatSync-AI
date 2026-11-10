package com.seatsync.modules.event.dto;

import com.seatsync.modules.event.entity.EventStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Venue ID is required")
    private Long venueId;

    @NotBlank(message = "Event title is required")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @Size(max = 250, message = "Slug cannot exceed 250 characters")
    private String slug;

    @NotBlank(message = "Short description is required")
    @Size(max = 500, message = "Short description cannot exceed 500 characters")
    private String shortDescription;

    private String fullDescription;

    @Size(max = 500, message = "Banner image URL cannot exceed 500 characters")
    private String bannerImageUrl;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    private Instant startTime;

    @NotNull(message = "End time is required")
    private Instant endTime;

    private Instant doorsOpenTime;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.00", message = "Base price cannot be negative")
    private BigDecimal basePrice;

    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;
}

