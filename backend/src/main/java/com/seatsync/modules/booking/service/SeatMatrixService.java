package com.seatsync.modules.booking.service;

import com.seatsync.modules.booking.dto.response.EventSeatMatrixResponse;

public interface SeatMatrixService {
    EventSeatMatrixResponse getEventSeatMatrix(Long eventId);
}

