package com.seatsync.modules.booking.controller;

import com.seatsync.common.api.ApiResponse;
import com.seatsync.modules.booking.dto.response.EventSeatMatrixResponse;
import com.seatsync.modules.booking.service.SeatMatrixService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing real-time event seat matrix and availability.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class SeatMatrixController {

    private final SeatMatrixService seatMatrixService;

    @GetMapping("/{eventId}/seats")
    public ResponseEntity<ApiResponse<EventSeatMatrixResponse>> getEventSeatMatrix(@PathVariable Long eventId) {
        EventSeatMatrixResponse response = seatMatrixService.getEventSeatMatrix(eventId);
        return ResponseEntity.ok(ApiResponse.success("Event seat matrix retrieved successfully", response));
    }
}

