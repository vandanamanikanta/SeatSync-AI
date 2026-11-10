package com.seatsync.modules.booking.repository;

import com.seatsync.modules.booking.entity.Booking;
import com.seatsync.modules.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingReference(String bookingReference);
    Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    Optional<Booking> findByIdAndUserId(Long id, Long userId);
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByEventIdAndStatus(Long eventId, BookingStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.event e " +
           "JOIN FETCH e.venue v " +
           "JOIN FETCH b.user u " +
           "WHERE b.id = :id")
    Optional<Booking> findByIdWithEventDetails(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.event e " +
           "JOIN FETCH e.venue v " +
           "JOIN FETCH b.user u " +
           "WHERE b.user.id = :userId " +
           "ORDER BY b.createdAt DESC")
    List<Booking> findByUserIdWithEventDetails(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.event e " +
           "JOIN FETCH e.venue v " +
           "JOIN FETCH b.user u " +
           "WHERE b.user.id = :userId AND b.idempotencyKey = :idempotencyKey")
    Optional<Booking> findByUserIdAndIdempotencyKeyWithEventDetails(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("idempotencyKey") String idempotencyKey);
}

