package com.seatsync.modules.event.dto;

import com.seatsync.modules.event.entity.EventCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCategoryResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String iconName;
    private Integer displayOrder;
    private Boolean isActive;
    private Instant createdAt;

    public static EventCategoryResponse fromEntity(EventCategory category) {
        return EventCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .iconName(category.getIconName())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .build();
    }
}

