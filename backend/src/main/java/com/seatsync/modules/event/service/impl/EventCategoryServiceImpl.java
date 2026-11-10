package com.seatsync.modules.event.service.impl;

import com.seatsync.common.exception.ConflictException;
import com.seatsync.common.exception.ResourceNotFoundException;
import com.seatsync.modules.event.dto.CreateCategoryRequest;
import com.seatsync.modules.event.dto.EventCategoryResponse;
import com.seatsync.modules.event.entity.EventCategory;
import com.seatsync.modules.event.repository.EventCategoryRepository;
import com.seatsync.modules.event.service.EventCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventCategoryServiceImpl implements EventCategoryService {

    private final EventCategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EventCategoryResponse> getAllActiveCategories() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(EventCategoryResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventCategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(EventCategoryResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventCategoryResponse getCategoryById(Long id) {
        EventCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventCategory", "id", id));
        return EventCategoryResponse.fromEntity(category);
    }

    @Override
    @Transactional
    public EventCategoryResponse createCategory(CreateCategoryRequest request) {
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = generateSlug(request.getName());
        } else {
            slug = slug.trim().toLowerCase();
        }

        if (categoryRepository.existsByName(request.getName().trim())) {
            throw new ConflictException("Category with name '" + request.getName() + "' already exists", "CATEGORY_NAME_EXISTS");
        }
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category with slug '" + slug + "' already exists", "CATEGORY_SLUG_EXISTS");
        }

        EventCategory category = EventCategory.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .iconName(request.getIconName())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        EventCategory saved = categoryRepository.save(category);
        log.info("Created event category ID: {} with slug: {}", saved.getId(), saved.getSlug());
        return EventCategoryResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public EventCategoryResponse updateCategory(Long id, CreateCategoryRequest request) {
        EventCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventCategory", "id", id));

        if (!category.getName().equalsIgnoreCase(request.getName().trim()) &&
                categoryRepository.existsByName(request.getName().trim())) {
            throw new ConflictException("Category with name '" + request.getName() + "' already exists", "CATEGORY_NAME_EXISTS");
        }

        category.setName(request.getName().trim());
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription().trim());
        }
        if (request.getIconName() != null) {
            category.setIconName(request.getIconName().trim());
        }
        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        EventCategory updated = categoryRepository.save(category);
        return EventCategoryResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("EventCategory", "id", id);
        }
        categoryRepository.deleteById(id);
        log.info("Deleted event category ID: {}", id);
    }

    private String generateSlug(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
}

