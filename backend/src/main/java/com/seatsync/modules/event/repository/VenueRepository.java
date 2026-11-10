package com.seatsync.modules.event.repository;

import com.seatsync.modules.event.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {

    Optional<Venue> findBySlug(String slug);

    Page<Venue> findByCityIgnoreCase(String city, Pageable pageable);

    boolean existsBySlug(String slug);
}

