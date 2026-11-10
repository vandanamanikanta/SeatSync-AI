package com.seatsync.modules.event.repository;

import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT e FROM Event e " +
           "JOIN FETCH e.organizer " +
           "JOIN FETCH e.category " +
           "JOIN FETCH e.venue " +
           "WHERE e.id = :id")
    Optional<Event> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT e FROM Event e " +
           "JOIN FETCH e.organizer " +
           "JOIN FETCH e.category " +
           "JOIN FETCH e.venue " +
           "WHERE e.slug = :slug")
    Optional<Event> findBySlugWithDetails(@Param("slug") String slug);

    Page<Event> findByOrganizerId(Long organizerId, Pageable pageable);

    Page<Event> findByStatus(EventStatus status, Pageable pageable);
}

