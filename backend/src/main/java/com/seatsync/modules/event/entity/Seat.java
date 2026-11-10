package com.seatsync.modules.event.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Physical seat entity belonging to a specific venue section.
 */
@Entity
@Table(name = "seats", uniqueConstraints = {
        @UniqueConstraint(name = "uk_section_row_seat", columnNames = {"venue_section_id", "row_label", "seat_number"})
}, indexes = {
        @Index(name = "idx_seats_section", columnList = "venue_section_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_section_id", nullable = false)
    private VenueSection venueSection;

    @Column(name = "row_label", nullable = false, length = 10)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Column(name = "grid_x", nullable = false)
    @Builder.Default
    private Integer gridX = 0;

    @Column(name = "grid_y", nullable = false)
    @Builder.Default
    private Integer gridY = 0;

    @Column(name = "is_accessible")
    @Builder.Default
    private Boolean isAccessible = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

