package com.seatsync.modules.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Detailed booking confirmation payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long bookingId;
    private String bookingReference;
    private Long eventId;
    private String eventTitle;
    private String venueName;
    private Long userId;
    private String customerEmail;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private String idempotencyKey;
    private Instant createdAt;
    private List<BookingItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingItemDto {
        private Long itemId;
        private Long eventSeatId;
        private Long seatId;
        private String sectionName;
        private String rowLabel;
        private Integer seatNumber;
        private BigDecimal unitPrice;
        private String status;
    }
}

