package com.seatsync.modules.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 120, message = "Slug cannot exceed 120 characters")
    private String slug;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @Size(max = 50, message = "Icon name cannot exceed 50 characters")
    private String iconName;

    private Integer displayOrder;

    @Builder.Default
    private Boolean isActive = true;
}

