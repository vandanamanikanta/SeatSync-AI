package com.seatsync.modules.event.service;

import com.seatsync.modules.event.dto.CreateCategoryRequest;
import com.seatsync.modules.event.dto.EventCategoryResponse;

import java.util.List;

public interface EventCategoryService {

    List<EventCategoryResponse> getAllActiveCategories();

    List<EventCategoryResponse> getAllCategories();

    EventCategoryResponse getCategoryById(Long id);

    EventCategoryResponse createCategory(CreateCategoryRequest request);

    EventCategoryResponse updateCategory(Long id, CreateCategoryRequest request);

    void deleteCategory(Long id);
}

