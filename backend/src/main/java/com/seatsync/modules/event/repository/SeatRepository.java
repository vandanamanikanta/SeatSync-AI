package com.seatsync.modules.event.repository;

import com.seatsync.modules.event.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByVenueSectionIdOrderByRowLabelAscSeatNumberAsc(Long venueSectionId);
    List<Seat> findByVenueSectionVenueId(Long venueId);
}

