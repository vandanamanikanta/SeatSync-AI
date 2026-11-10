package com.seatsync.modules.event.repository;

import com.seatsync.modules.event.entity.VenueSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VenueSectionRepository extends JpaRepository<VenueSection, Long> {
    List<VenueSection> findByVenueIdOrderByDisplayOrderAsc(Long venueId);
    Optional<VenueSection> findByVenueIdAndSectionCode(Long venueId, String sectionCode);
}

