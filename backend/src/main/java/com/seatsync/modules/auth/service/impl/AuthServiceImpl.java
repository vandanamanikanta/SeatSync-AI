package com.seatsync.modules.auth.service.impl;

import com.seatsync.common.exception.BadRequestException;
import com.seatsync.common.exception.ConflictException;
import com.seatsync.common.exception.UnauthorizedException;
import com.seatsync.common.security.JwtTokenProvider;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.auth.dto.AuthResponse;
import com.seatsync.modules.auth.dto.LoginRequest;
import com.seatsync.modules.auth.dto.RefreshTokenRequest;
import com.seatsync.modules.auth.dto.RegisterRequest;
import com.seatsync.modules.auth.service.AuthService;
import com.seatsync.modules.user.entity.Role;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.entity.UserPreference;
import com.seatsync.modules.user.entity.UserStatus;
import com.seatsync.modules.user.repository.RoleRepository;
import com.seatsync.modules.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered: " + normalizedEmail, "EMAIL_ALREADY_EXISTS");
        }

        // Determine user role
        String roleName = "ROLE_CUSTOMER";
        if (request.getRole() != null && !request.getRole().isBlank()) {
            String requestedRole = request.getRole().trim().toUpperCase();
            if ("ROLE_ORGANIZER".equals(requestedRole) || "ORGANIZER".equals(requestedRole)) {
                roleName = "ROLE_ORGANIZER";
            } else if ("ROLE_ADMIN".equals(requestedRole) || "ADMIN".equals(requestedRole)) {
                throw new BadRequestException("Registration for administrator role is not permitted", "ROLE_NOT_PERMITTED");
            }
        }

        final String finalRoleName = roleName;
        Role userRole = roleRepository.findByName(finalRoleName)
                .orElseGet(() -> roleRepository.save(new Role(finalRoleName, "Auto-created role " + finalRoleName)));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .build();

        // Attach initial default preferences
        UserPreference preference = UserPreference.builder()
                .user(user)
                .budgetMin(BigDecimal.ZERO)
                .budgetMax(new BigDecimal("10000.00"))
                .build();
        user.setPreference(preference);

        User savedUser = userRepository.save(user);
        log.info("Registered new user with ID: {} and role: {}", savedUser.getId(), roleName);

        UserPrincipal principal = UserPrincipal.create(savedUser);
        String accessToken = tokenProvider.generateAccessToken(principal);
        String refreshToken = tokenProvider.generateRefreshToken(principal);

        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findByIdWithRolesAndPreference(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("User account not found"));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("Your account has been suspended. Please contact support.", "ACCOUNT_SUSPENDED");
        }

        String accessToken = tokenProvider.generateAccessToken(principal);
        String refreshToken = tokenProvider.generateRefreshToken(principal);

        log.info("User {} logged in successfully", normalizedEmail);
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();

        if (!tokenProvider.validateToken(token)) {
            throw new UnauthorizedException("Invalid or expired refresh token", "INVALID_REFRESH_TOKEN");
        }

        Claims claims = tokenProvider.getClaimsFromToken(token);
        String tokenType = claims.get("tokenType", String.class);
        if (!"REFRESH".equals(tokenType)) {
            throw new UnauthorizedException("Supplied token is not a valid refresh token", "INVALID_TOKEN_TYPE");
        }

        String email = claims.getSubject();
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new UnauthorizedException("User not found for token"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Account is not active", "ACCOUNT_INACTIVE");
        }

        UserPrincipal principal = UserPrincipal.create(user);
        String newAccessToken = tokenProvider.generateAccessToken(principal);

        return buildAuthResponse(user, newAccessToken, token);
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        AuthResponse.UserSummary summary = AuthResponse.UserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roleNames)
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenExpirationSeconds())
                .user(summary)
                .build();
    }
}

