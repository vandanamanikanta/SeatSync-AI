package com.seatsync.modules.event.service;

import com.seatsync.common.api.PageResponse;
import com.seatsync.modules.event.dto.CreateVenueRequest;
import com.seatsync.modules.event.dto.VenueResponse;
import org.springframework.data.domain.Pageable;

public interface VenueService {

    PageResponse<VenueResponse> getAllVenues(Pageable pageable);

    PageResponse<VenueResponse> getVenuesByCity(String city, Pageable pageable);

    VenueResponse getVenueById(Long id);

    VenueResponse getVenueBySlug(String slug);

    VenueResponse createVenue(CreateVenueRequest request);

    VenueResponse updateVenue(Long id, CreateVenueRequest request);
}

