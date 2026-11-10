package com.seatsync.modules.user.dto;

import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * User profile view DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private UserStatus status;
    private List<String> roles;
    private PreferenceSummary preference;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferenceSummary {
        private String preferredCity;
        private BigDecimal budgetMin;
        private BigDecimal budgetMax;
        private String favoriteCategories;
    }

    public static UserProfileResponse fromEntity(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(r -> r.getName())
                .toList();

        PreferenceSummary pref = null;
        if (user.getPreference() != null) {
            pref = PreferenceSummary.builder()
                    .preferredCity(user.getPreference().getPreferredCity())
                    .budgetMin(user.getPreference().getBudgetMin())
                    .budgetMax(user.getPreference().getBudgetMax())
                    .favoriteCategories(user.getPreference().getFavoriteCategories())
                    .build();
        }

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roleNames)
                .preference(pref)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

