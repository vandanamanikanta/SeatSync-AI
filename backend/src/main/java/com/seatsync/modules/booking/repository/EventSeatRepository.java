package com.seatsync.modules.booking.repository;

import com.seatsync.modules.booking.entity.EventSeat;
import com.seatsync.modules.booking.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    @Query("SELECT es FROM EventSeat es " +
           "JOIN FETCH es.seat s " +
           "JOIN FETCH s.venueSection vs " +
           "WHERE es.event.id = :eventId " +
           "ORDER BY vs.displayOrder ASC, s.rowLabel ASC, s.seatNumber ASC")
    List<EventSeat> findByEventIdWithSeatDetails(@Param("eventId") Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT es FROM EventSeat es " +
           "WHERE es.event.id = :eventId AND es.id IN :seatIds")
    List<EventSeat> findByIdInAndEventIdForUpdate(
            @Param("seatIds") Collection<Long> seatIds,
            @Param("eventId") Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT es FROM EventSeat es " +
           "WHERE es.event.id = :eventId AND es.holdToken = :holdToken")
    List<EventSeat> findByEventIdAndHoldTokenForUpdate(
            @Param("eventId") Long eventId,
            @Param("holdToken") String holdToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT es FROM EventSeat es " +
           "WHERE es.event.id = :eventId AND es.holdToken = :holdToken AND es.id IN :seatIds")
    List<EventSeat> findByEventIdAndHoldTokenAndIdInForUpdate(
            @Param("eventId") Long eventId,
            @Param("holdToken") String holdToken,
            @Param("seatIds") Collection<Long> seatIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT es FROM EventSeat es WHERE es.id = :id")
    Optional<EventSeat> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT es FROM EventSeat es " +
           "WHERE es.status = com.seatsync.modules.booking.entity.SeatStatus.HELD " +
           "AND es.heldUntil <= :now " +
           "AND es.currentBooking IS NULL")
    List<EventSeat> findExpiredHolds(@Param("now") Instant now);

    long countByEventIdAndStatus(Long eventId, SeatStatus status);

    long countByEventId(Long eventId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE EventSeat es " +
           "SET es.status = com.seatsync.modules.booking.entity.SeatStatus.BOOKED, " +
           "    es.currentBooking = :booking, " +
           "    es.heldByUser = null, " +
           "    es.heldUntil = null, " +
           "    es.holdToken = null, " +
           "    es.version = es.version + 1 " +
           "WHERE es.id IN :seatIds " +
           "  AND es.event.id = :eventId " +
           "  AND es.status = com.seatsync.modules.booking.entity.SeatStatus.HELD " +
           "  AND es.heldByUser.id = :userId " +
           "  AND es.heldUntil > :now " +
           "  AND es.holdToken = :holdToken " +
           "  AND es.currentBooking IS NULL")
    int confirmSeatsForBooking(
            @Param("seatIds") Collection<Long> seatIds,
            @Param("eventId") Long eventId,
            @Param("booking") com.seatsync.modules.booking.entity.Booking booking,
            @Param("userId") Long userId,
            @Param("holdToken") String holdToken,
            @Param("now") Instant now);
}

