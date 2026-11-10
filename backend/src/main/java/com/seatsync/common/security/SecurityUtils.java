package com.seatsync.common.security;

import com.seatsync.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Utility helper methods to query the current Spring Security context.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Get the current authenticated UserPrincipal, or empty if anonymous.
     */
    public static Optional<UserPrincipal> getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /**
     * Get the current authenticated UserPrincipal or throw UnauthorizedException.
     */
    public static UserPrincipal getRequiredCurrentUserPrincipal() {
        return getCurrentUserPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User is not authenticated"));
    }

    /**
     * Get current authenticated user ID or throw UnauthorizedException.
     */
    public static Long getCurrentUserId() {
        return getRequiredCurrentUserPrincipal().getId();
    }

    /**
     * Get current authenticated user email or throw UnauthorizedException.
     */
    public static String getCurrentUserEmail() {
        return getRequiredCurrentUserPrincipal().getEmail();
    }
}

