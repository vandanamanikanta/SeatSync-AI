package com.seatsync.event;

import com.seatsync.modules.event.entity.EventCategory;
import com.seatsync.modules.event.repository.EventCategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class EventCategoryRepositoryTest {

    @Autowired
    private EventCategoryRepository categoryRepository;

    @Test
    @DisplayName("Should persist and fetch active categories ordered by display order")
    void shouldFindActiveCategoriesOrdered() {
        EventCategory cat1 = EventCategory.builder()
                .name("Sports")
                .slug("sports")
                .displayOrder(2)
                .isActive(true)
                .build();

        EventCategory cat2 = EventCategory.builder()
                .name("Music")
                .slug("music")
                .displayOrder(1)
                .isActive(true)
                .build();

        EventCategory cat3 = EventCategory.builder()
                .name("Inactive Category")
                .slug("inactive")
                .displayOrder(0)
                .isActive(false)
                .build();

        categoryRepository.saveAll(List.of(cat1, cat2, cat3));

        List<EventCategory> activeCategories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        assertEquals(2, activeCategories.size());
        assertEquals("Music", activeCategories.get(0).getName());
        assertEquals("Sports", activeCategories.get(1).getName());

        Optional<EventCategory> bySlug = categoryRepository.findBySlug("music");
        assertTrue(bySlug.isPresent());
        assertEquals("Music", bySlug.get().getName());
    }
}

