package com.seatsync.modules.event.repository;

import com.seatsync.modules.event.entity.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventCategoryRepository extends JpaRepository<EventCategory, Long> {

    Optional<EventCategory> findBySlug(String slug);

    List<EventCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    boolean existsByName(String name);

    boolean existsBySlug(String slug);
}

