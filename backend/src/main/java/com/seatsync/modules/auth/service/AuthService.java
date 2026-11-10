package com.seatsync.modules.auth.service;

import com.seatsync.modules.auth.dto.AuthResponse;
import com.seatsync.modules.auth.dto.LoginRequest;
import com.seatsync.modules.auth.dto.RefreshTokenRequest;
import com.seatsync.modules.auth.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);
}

