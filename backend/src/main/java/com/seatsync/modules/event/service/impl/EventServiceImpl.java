package com.seatsync.modules.event.service.impl;

import com.seatsync.common.api.PageResponse;
import com.seatsync.common.exception.BadRequestException;
import com.seatsync.common.exception.ConflictException;
import com.seatsync.common.exception.ForbiddenException;
import com.seatsync.common.exception.ResourceNotFoundException;
import com.seatsync.modules.event.dto.CreateEventRequest;
import com.seatsync.modules.event.dto.EventResponse;
import com.seatsync.modules.event.dto.EventSearchCriteria;
import com.seatsync.modules.event.dto.UpdateEventRequest;
import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventCategory;
import com.seatsync.modules.event.entity.EventStatus;
import com.seatsync.modules.event.entity.Venue;
import com.seatsync.modules.event.repository.EventCategoryRepository;
import com.seatsync.modules.event.repository.EventRepository;
import com.seatsync.modules.event.repository.VenueRepository;
import com.seatsync.modules.event.service.EventService;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final VenueRepository venueRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchEvents(EventSearchCriteria criteria, Pageable pageable) {
        Page<EventResponse> page = eventRepository.findAll(criteria.toSpecification(), pageable)
                .map(EventResponse::fromEntity);
        return PageResponse.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id) {
        Event event = eventRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", id));
        return EventResponse.fromEntity(event);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventBySlug(String slug) {
        Event event = eventRepository.findBySlugWithDetails(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "slug", slug));
        return EventResponse.fromEntity(event);
    }

    @Override
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, Long organizerId) {
        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().equals(request.getStartTime())) {
            throw new BadRequestException("Event end time must be strictly after start time", "INVALID_EVENT_TIME");
        }

        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", organizerId));

        EventCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("EventCategory", "id", request.getCategoryId()));

        Venue venue = venueRepository.findById(request.getVenueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue", "id", request.getVenueId()));

        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = generateSlug(request.getTitle());
        } else {
            slug = slug.trim().toLowerCase();
        }

        // Ensure slug uniqueness
        if (eventRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        Event event = Event.builder()
                .organizer(organizer)
                .category(category)
                .venue(venue)
                .title(request.getTitle().trim())
                .slug(slug)
                .shortDescription(request.getShortDescription().trim())
                .fullDescription(request.getFullDescription())
                .bannerImageUrl(request.getBannerImageUrl())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .doorsOpenTime(request.getDoorsOpenTime())
                .basePrice(request.getBasePrice())
                .status(request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT)
                .build();

        Event saved = eventRepository.save(event);
        log.info("Created event ID: {} with slug: '{}' by organizer ID: {}", saved.getId(), saved.getSlug(), organizerId);
        return EventResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public EventResponse updateEvent(Long id, UpdateEventRequest request, Long currentUserId, boolean isAdmin) {
        Event event = eventRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", id));

        validateOwnership(event, currentUserId, isAdmin);

        if (request.getCategoryId() != null && !request.getCategoryId().equals(event.getCategory().getId())) {
            EventCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("EventCategory", "id", request.getCategoryId()));
            event.setCategory(category);
        }

        if (request.getVenueId() != null && !request.getVenueId().equals(event.getVenue().getId())) {
            Venue venue = venueRepository.findById(request.getVenueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Venue", "id", request.getVenueId()));
            event.setVenue(venue);
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            event.setTitle(request.getTitle().trim());
        }
        if (request.getShortDescription() != null && !request.getShortDescription().isBlank()) {
            event.setShortDescription(request.getShortDescription().trim());
        }
        if (request.getFullDescription() != null) {
            event.setFullDescription(request.getFullDescription());
        }
        if (request.getBannerImageUrl() != null) {
            event.setBannerImageUrl(request.getBannerImageUrl());
        }
        if (request.getStartTime() != null) {
            event.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            event.setEndTime(request.getEndTime());
        }
        if (request.getDoorsOpenTime() != null) {
            event.setDoorsOpenTime(request.getDoorsOpenTime());
        }
        if (request.getBasePrice() != null) {
            event.setBasePrice(request.getBasePrice());
        }
        if (request.getStatus() != null) {
            event.setStatus(request.getStatus());
        }

        if (event.getEndTime().isBefore(event.getStartTime()) || event.getEndTime().equals(event.getStartTime())) {
            throw new BadRequestException("Event end time must be strictly after start time", "INVALID_EVENT_TIME");
        }

        Event updated = eventRepository.save(event);
        log.info("Updated event ID: {}", id);
        return EventResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteEvent(Long id, Long currentUserId, boolean isAdmin) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", id));

        validateOwnership(event, currentUserId, isAdmin);

        eventRepository.delete(event);
        log.info("Deleted event ID: {}", id);
    }

    @Override
    @Transactional
    public EventResponse updateEventStatus(Long id, EventStatus status, Long currentUserId, boolean isAdmin) {
        Event event = eventRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", id));

        validateOwnership(event, currentUserId, isAdmin);

        event.setStatus(status);
        Event updated = eventRepository.save(event);
        log.info("Updated status of event ID: {} to {}", id, status);
        return EventResponse.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getEventsByOrganizer(Long organizerId, Pageable pageable) {
        Page<EventResponse> page = eventRepository.findByOrganizerId(organizerId, pageable)
                .map(EventResponse::fromEntity);
        return PageResponse.of(page);
    }

    private void validateOwnership(Event event, Long currentUserId, boolean isAdmin) {
        if (!isAdmin && !event.getOrganizer().getId().equals(currentUserId)) {
            throw new ForbiddenException("You do not have permission to modify this event", "EVENT_PERMISSION_DENIED");
        }
    }

    private String generateSlug(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
}

