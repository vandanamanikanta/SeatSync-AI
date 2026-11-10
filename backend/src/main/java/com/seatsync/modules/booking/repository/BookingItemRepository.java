package com.seatsync.modules.booking.repository;

import com.seatsync.modules.booking.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    @Query("SELECT bi FROM BookingItem bi " +
           "JOIN FETCH bi.eventSeat es " +
           "JOIN FETCH es.seat s " +
           "JOIN FETCH s.venueSection vs " +
           "WHERE bi.booking.id = :bookingId")
    List<BookingItem> findByBookingIdWithDetails(@Param("bookingId") Long bookingId);

    List<BookingItem> findByBookingId(Long bookingId);
}

