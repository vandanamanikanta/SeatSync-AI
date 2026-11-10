package com.seatsync.security;

import com.seatsync.common.security.JwtTokenProvider;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.user.entity.Role;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        // 256-bit test secret
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpirationMs", 60000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpirationMs", 3600000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtIssuer", "SeatSync-AI-Test");
        jwtTokenProvider.init();
    }

    @Test
    void shouldGenerateAndValidateAccessToken() {
        User user = User.builder()
                .id(101L)
                .email("testuser@seatsync.ai")
                .passwordHash("hashedpassword")
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(new Role("ROLE_CUSTOMER", "Customer Role")))
                .build();

        UserPrincipal principal = UserPrincipal.create(user);
        String token = jwtTokenProvider.generateAccessToken(principal);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("testuser@seatsync.ai", jwtTokenProvider.getUsernameFromToken(token));
        assertEquals(101L, jwtTokenProvider.getUserIdFromToken(token));
    }

    @Test
    void shouldGenerateAndValidateRefreshToken() {
        User user = User.builder()
                .id(102L)
                .email("refresh@seatsync.ai")
                .passwordHash("hashedpassword")
                .fullName("Refresh User")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(new Role("ROLE_ORGANIZER", "Organizer Role")))
                .build();

        UserPrincipal principal = UserPrincipal.create(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        assertNotNull(refreshToken);
        assertTrue(jwtTokenProvider.validateToken(refreshToken));
        assertEquals("refresh@seatsync.ai", jwtTokenProvider.getUsernameFromToken(refreshToken));
        assertEquals("REFRESH", jwtTokenProvider.getClaimsFromToken(refreshToken).get("tokenType"));
    }

    @Test
    void shouldRejectInvalidOrTamperedToken() {
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.payload";
        assertFalse(jwtTokenProvider.validateToken(invalidToken));
    }
}

