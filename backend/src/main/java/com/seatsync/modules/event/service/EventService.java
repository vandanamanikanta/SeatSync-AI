package com.seatsync.modules.event.service;

import com.seatsync.common.api.PageResponse;
import com.seatsync.modules.event.dto.CreateEventRequest;
import com.seatsync.modules.event.dto.EventResponse;
import com.seatsync.modules.event.dto.EventSearchCriteria;
import com.seatsync.modules.event.dto.UpdateEventRequest;
import com.seatsync.modules.event.entity.EventStatus;
import org.springframework.data.domain.Pageable;

public interface EventService {

    PageResponse<EventResponse> searchEvents(EventSearchCriteria criteria, Pageable pageable);

    EventResponse getEventById(Long id);

    EventResponse getEventBySlug(String slug);

    EventResponse createEvent(CreateEventRequest request, Long currentUserId);

    EventResponse updateEvent(Long id, UpdateEventRequest request, Long currentUserId, boolean isAdmin);

    void deleteEvent(Long id, Long currentUserId, boolean isAdmin);

    EventResponse updateEventStatus(Long id, EventStatus status, Long currentUserId, boolean isAdmin);

    PageResponse<EventResponse> getEventsByOrganizer(Long organizerId, Pageable pageable);
}

