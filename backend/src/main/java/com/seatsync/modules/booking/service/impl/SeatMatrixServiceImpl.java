package com.seatsync.modules.booking.service.impl;

import com.seatsync.common.exception.ResourceNotFoundException;
import com.seatsync.modules.booking.dto.response.EventSeatMatrixResponse;
import com.seatsync.modules.booking.entity.EventSeat;
import com.seatsync.modules.booking.entity.SeatStatus;
import com.seatsync.modules.booking.repository.EventSeatRepository;
import com.seatsync.modules.booking.service.SeatMatrixService;
import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.Seat;
import com.seatsync.modules.event.entity.VenueSection;
import com.seatsync.modules.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatMatrixServiceImpl implements SeatMatrixService {

    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;

    @Override
    @Transactional(readOnly = true)
    public EventSeatMatrixResponse getEventSeatMatrix(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        List<EventSeat> eventSeats = eventSeatRepository.findByEventIdWithSeatDetails(eventId);
        Instant now = Instant.now();

        // Group event seats by VenueSection
        Map<VenueSection, List<EventSeat>> sectionMap = eventSeats.stream()
                .collect(Collectors.groupingBy(
                        es -> es.getSeat().getVenueSection(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int totalSeats = eventSeats.size();
        int availableSeats = 0;
        int heldSeats = 0;
        int bookedSeats = 0;

        List<EventSeatMatrixResponse.SectionSeatMapDto> sectionDtos = new ArrayList<>();

        for (Map.Entry<VenueSection, List<EventSeat>> entry : sectionMap.entrySet()) {
            VenueSection section = entry.getKey();
            List<EventSeat> seatsInSec = entry.getValue();

            int sectionAvailable = 0;
            List<EventSeatMatrixResponse.SeatDto> seatDtos = new ArrayList<>();

            for (EventSeat es : seatsInSec) {
                Seat seat = es.getSeat();
                SeatStatus effectiveStatus = es.getStatus();

                // Check expired hold
                if (effectiveStatus == SeatStatus.HELD && es.isHoldExpired(now)) {
                    effectiveStatus = SeatStatus.AVAILABLE;
                }

                if (effectiveStatus == SeatStatus.AVAILABLE) {
                    availableSeats++;
                    sectionAvailable++;
                } else if (effectiveStatus == SeatStatus.HELD) {
                    heldSeats++;
                } else if (effectiveStatus == SeatStatus.BOOKED) {
                    bookedSeats++;
                }

                seatDtos.add(EventSeatMatrixResponse.SeatDto.builder()
                        .eventSeatId(es.getId())
                        .seatId(seat.getId())
                        .rowLabel(seat.getRowLabel())
                        .seatNumber(seat.getSeatNumber())
                        .gridX(seat.getGridX())
                        .gridY(seat.getGridY())
                        .status(effectiveStatus.name())
                        .price(es.getPrice())
                        .isAccessible(seat.getIsAccessible())
                        .heldUntil(effectiveStatus == SeatStatus.HELD ? es.getHeldUntil() : null)
                        .build());
            }

            sectionDtos.add(EventSeatMatrixResponse.SectionSeatMapDto.builder()
                    .sectionId(section.getId())
                    .sectionName(section.getName())
                    .sectionCode(section.getSectionCode())
                    .tierLevel(section.getTierLevel())
                    .price(seatDtos.isEmpty() ? null : seatDtos.get(0).getPrice())
                    .capacity(section.getCapacity())
                    .availableSeats(sectionAvailable)
                    .seats(seatDtos)
                    .build());
        }

        return EventSeatMatrixResponse.builder()
                .eventId(event.getId())
                .eventTitle(event.getTitle())
                .venueId(event.getVenue().getId())
                .venueName(event.getVenue().getName())
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .heldSeats(heldSeats)
                .bookedSeats(bookedSeats)
                .sections(sectionDtos)
                .build();
    }
}

