package com.seatsync.modules.event.dto;

import com.seatsync.modules.event.entity.Venue;
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
public class VenueResponse {

    private Long id;
    private String name;
    private String slug;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private Integer capacity;
    private String timezone;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Instant createdAt;

    public static VenueResponse fromEntity(Venue venue) {
        return VenueResponse.builder()
                .id(venue.getId())
                .name(venue.getName())
                .slug(venue.getSlug())
                .address(venue.getAddress())
                .city(venue.getCity())
                .state(venue.getState())
                .postalCode(venue.getPostalCode())
                .country(venue.getCountry())
                .capacity(venue.getCapacity())
                .timezone(venue.getTimezone())
                .latitude(venue.getLatitude())
                .longitude(venue.getLongitude())
                .createdAt(venue.getCreatedAt())
                .build();
    }
}

