package com.seatsync.modules.event.dto;

import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventStatus;
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
public class EventResponse {

    private Long id;
    private String title;
    private String slug;
    private String shortDescription;
    private String fullDescription;
    private String bannerImageUrl;
    private Instant startTime;
    private Instant endTime;
    private Instant doorsOpenTime;
    private BigDecimal basePrice;
    private EventStatus status;
    private CategorySummary category;
    private VenueSummary venue;
    private OrganizerSummary organizer;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private Long id;
        private String name;
        private String slug;
        private String iconName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VenueSummary {
        private Long id;
        private String name;
        private String city;
        private String state;
        private String address;
        private Integer capacity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizerSummary {
        private Long id;
        private String fullName;
        private String email;
    }

    public static EventResponse fromEntity(Event event) {
        CategorySummary cat = null;
        if (event.getCategory() != null) {
            cat = CategorySummary.builder()
                    .id(event.getCategory().getId())
                    .name(event.getCategory().getName())
                    .slug(event.getCategory().getSlug())
                    .iconName(event.getCategory().getIconName())
                    .build();
        }

        VenueSummary ven = null;
        if (event.getVenue() != null) {
            ven = VenueSummary.builder()
                    .id(event.getVenue().getId())
                    .name(event.getVenue().getName())
                    .city(event.getVenue().getCity())
                    .state(event.getVenue().getState())
                    .address(event.getVenue().getAddress())
                    .capacity(event.getVenue().getCapacity())
                    .build();
        }

        OrganizerSummary org = null;
        if (event.getOrganizer() != null) {
            org = OrganizerSummary.builder()
                    .id(event.getOrganizer().getId())
                    .fullName(event.getOrganizer().getFullName())
                    .email(event.getOrganizer().getEmail())
                    .build();
        }

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .slug(event.getSlug())
                .shortDescription(event.getShortDescription())
                .fullDescription(event.getFullDescription())
                .bannerImageUrl(event.getBannerImageUrl())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .doorsOpenTime(event.getDoorsOpenTime())
                .basePrice(event.getBasePrice())
                .status(event.getStatus())
                .category(cat)
                .venue(ven)
                .organizer(org)
                .createdAt(event.getCreatedAt())
                .build();
    }
}

