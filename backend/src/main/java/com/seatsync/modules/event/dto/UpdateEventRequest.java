package com.seatsync.modules.event.dto;

import com.seatsync.modules.event.entity.EventStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
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
public class UpdateEventRequest {

    private Long categoryId;
    private Long venueId;

    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @Size(max = 250, message = "Slug cannot exceed 250 characters")
    private String slug;

    @Size(max = 500, message = "Short description cannot exceed 500 characters")
    private String shortDescription;

    private String fullDescription;

    @Size(max = 500, message = "Banner image URL cannot exceed 500 characters")
    private String bannerImageUrl;

    private Instant startTime;
    private Instant endTime;
    private Instant doorsOpenTime;

    @DecimalMin(value = "0.00", message = "Base price cannot be negative")
    private BigDecimal basePrice;

    private EventStatus status;
}

