package com.seatsync.modules.user.service.impl;

import com.seatsync.common.exception.BadRequestException;
import com.seatsync.common.exception.ResourceNotFoundException;
import com.seatsync.common.security.SecurityUtils;
import com.seatsync.modules.user.dto.ChangePasswordRequest;
import com.seatsync.modules.user.dto.UpdateProfileRequest;
import com.seatsync.modules.user.dto.UserProfileResponse;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.entity.UserPreference;
import com.seatsync.modules.user.repository.UserPreferenceRepository;
import com.seatsync.modules.user.repository.UserRepository;
import com.seatsync.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        return getUserProfileById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileById(Long id) {
        User user = userRepository.findByIdWithRolesAndPreference(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        return UserProfileResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findByIdWithRolesAndPreference(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }

        // Update preferences
        UserPreference preference = user.getPreference();
        if (preference == null) {
            preference = UserPreference.builder()
                    .user(user)
                    .build();
            user.setPreference(preference);
        }

        if (request.getPreferredCity() != null) {
            preference.setPreferredCity(request.getPreferredCity().trim());
        }
        if (request.getBudgetMin() != null) {
            preference.setBudgetMin(request.getBudgetMin());
        }
        if (request.getBudgetMax() != null) {
            preference.setBudgetMax(request.getBudgetMax());
        }
        if (request.getFavoriteCategories() != null) {
            preference.setFavoriteCategories(request.getFavoriteCategories());
        }

        userRepository.save(user);
        log.info("Profile updated successfully for user ID: {}", userId);

        return UserProfileResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password does not match", "INVALID_CURRENT_PASSWORD");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("New password must be different from the current password", "PASSWORD_UNCHANGED");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password successfully changed for user ID: {}", userId);
    }
}

