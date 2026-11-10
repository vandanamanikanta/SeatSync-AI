package com.seatsync.modules.event.service.impl;

import com.seatsync.common.api.PageResponse;
import com.seatsync.common.exception.ConflictException;
import com.seatsync.common.exception.ResourceNotFoundException;
import com.seatsync.modules.event.dto.CreateVenueRequest;
import com.seatsync.modules.event.dto.VenueResponse;
import com.seatsync.modules.event.entity.Venue;
import com.seatsync.modules.event.repository.VenueRepository;
import com.seatsync.modules.event.service.VenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> getAllVenues(Pageable pageable) {
        Page<VenueResponse> page = venueRepository.findAll(pageable)
                .map(VenueResponse::fromEntity);
        return PageResponse.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> getVenuesByCity(String city, Pageable pageable) {
        Page<VenueResponse> page = venueRepository.findByCityIgnoreCase(city, pageable)
                .map(VenueResponse::fromEntity);
        return PageResponse.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getVenueById(Long id) {
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", "id", id));
        return VenueResponse.fromEntity(venue);
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getVenueBySlug(String slug) {
        Venue venue = venueRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", "slug", slug));
        return VenueResponse.fromEntity(venue);
    }

    @Override
    @Transactional
    public VenueResponse createVenue(CreateVenueRequest request) {
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = generateSlug(request.getName() + "-" + request.getCity());
        } else {
            slug = slug.trim().toLowerCase();
        }

        if (venueRepository.existsBySlug(slug)) {
            throw new ConflictException("Venue with slug '" + slug + "' already exists", "VENUE_SLUG_EXISTS");
        }

        Venue venue = Venue.builder()
                .name(request.getName().trim())
                .slug(slug)
                .address(request.getAddress().trim())
                .city(request.getCity().trim())
                .state(request.getState().trim())
                .postalCode(request.getPostalCode().trim())
                .country(request.getCountry() != null ? request.getCountry().trim() : "India")
                .capacity(request.getCapacity())
                .timezone(request.getTimezone() != null ? request.getTimezone().trim() : "Asia/Kolkata")
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        Venue saved = venueRepository.save(venue);
        log.info("Created venue ID: {} with slug: {}", saved.getId(), saved.getSlug());
        return VenueResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public VenueResponse updateVenue(Long id, CreateVenueRequest request) {
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", "id", id));

        venue.setName(request.getName().trim());
        venue.setAddress(request.getAddress().trim());
        venue.setCity(request.getCity().trim());
        venue.setState(request.getState().trim());
        venue.setPostalCode(request.getPostalCode().trim());
        if (request.getCountry() != null) {
            venue.setCountry(request.getCountry().trim());
        }
        venue.setCapacity(request.getCapacity());
        if (request.getTimezone() != null) {
            venue.setTimezone(request.getTimezone().trim());
        }
        if (request.getLatitude() != null) {
            venue.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            venue.setLongitude(request.getLongitude());
        }

        Venue updated = venueRepository.save(venue);
        return VenueResponse.fromEntity(updated);
    }

    private String generateSlug(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
}

