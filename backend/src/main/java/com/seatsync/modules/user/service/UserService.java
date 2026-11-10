package com.seatsync.modules.user.service;

import com.seatsync.modules.user.dto.ChangePasswordRequest;
import com.seatsync.modules.user.dto.UpdateProfileRequest;
import com.seatsync.modules.user.dto.UserProfileResponse;

public interface UserService {

    UserProfileResponse getCurrentUserProfile();

    UserProfileResponse getUserProfileById(Long id);

    UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);
}

