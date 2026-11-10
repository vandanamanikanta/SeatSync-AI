package com.seatsync.modules.booking.entity;

import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.Seat;
import com.seatsync.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event-specific seat instance representing real-time seat status, price, and holds.
 */
@Entity
@Table(name = "event_seats", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_seat", columnNames = {"event_id", "seat_id"})
}, indexes = {
        @Index(name = "idx_es_status", columnList = "event_id, status"),
        @Index(name = "idx_es_held_until", columnList = "held_until"),
        @Index(name = "idx_es_held_by", columnList = "held_by_user_id"),
        @Index(name = "idx_es_current_booking", columnList = "current_booking_id"),
        @Index(name = "idx_es_hold_token", columnList = "hold_token")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_booking_id")
    private Booking currentBooking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "held_by_user_id")
    private User heldByUser;

    @Column(name = "held_until")
    private Instant heldUntil;

    @Column(name = "hold_token", length = 64)
    private String holdToken;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Helper to determine whether the hold on this seat has expired.
     */
    public boolean isHoldExpired(Instant now) {
        return status == SeatStatus.HELD && heldUntil != null && heldUntil.isBefore(now);
    }

    /**
     * Helper to determine whether this seat is effectively available for hold.
     */
    public boolean isAvailableForHold(Instant now) {
        if (currentBooking != null) {
            return false;
        }
        if (status == SeatStatus.AVAILABLE) {
            return true;
        }
        return isHoldExpired(now);
    }
}

