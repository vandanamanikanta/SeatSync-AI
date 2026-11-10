package com.seatsync.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * User recommendation and event search preferences matching 'user_preferences' table.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "preferred_city", length = 100)
    private String preferredCity;

    @Column(name = "budget_min", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal budgetMin = BigDecimal.ZERO;

    @Column(name = "budget_max", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal budgetMax = new BigDecimal("10000.00");

    @Column(name = "favorite_categories", columnDefinition = "JSON")
    private String favoriteCategories;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

