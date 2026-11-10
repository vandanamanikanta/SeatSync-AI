package com.seatsync.modules.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Hierarchical seat map response for an event grouped by venue sections.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSeatMatrixResponse {

    private Long eventId;
    private String eventTitle;
    private Long venueId;
    private String venueName;
    private int totalSeats;
    private int availableSeats;
    private int heldSeats;
    private int bookedSeats;
    private List<SectionSeatMapDto> sections;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionSeatMapDto {
        private Long sectionId;
        private String sectionName;
        private String sectionCode;
        private Integer tierLevel;
        private BigDecimal price;
        private Integer capacity;
        private Integer availableSeats;
        private List<SeatDto> seats;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatDto {
        private Long eventSeatId;
        private Long seatId;
        private String rowLabel;
        private Integer seatNumber;
        private Integer gridX;
        private Integer gridY;
        private String status;
        private BigDecimal price;
        private Boolean isAccessible;
        private Instant heldUntil;
    }
}

