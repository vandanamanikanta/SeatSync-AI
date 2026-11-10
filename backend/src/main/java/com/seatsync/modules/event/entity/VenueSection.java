package com.seatsync.modules.event.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Venue section entity representing physical zones (e.g., VIP, Platinum, Gold) within a venue.
 */
@Entity
@Table(name = "venue_sections", uniqueConstraints = {
        @UniqueConstraint(name = "uk_venue_section", columnNames = {"venue_id", "section_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class VenueSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "section_code", nullable = false, length = 20)
    private String sectionCode;

    @Column(name = "tier_level")
    @Builder.Default
    private Integer tierLevel = 1;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

