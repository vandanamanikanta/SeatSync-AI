package com.seatsync.booking;

import com.seatsync.common.config.RedisConfig;
import com.seatsync.modules.booking.redis.RedisSeatHoldCoordinator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RedisSeatHoldCoordinatorTest {

    @Autowired
    private RedisSeatHoldCoordinator coordinator;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final Long eventId = 999L;
    private final List<Long> seatIds = Arrays.asList(101L, 102L, 103L);

    @BeforeEach
    @AfterEach
    void cleanUpRedis() {
        for (Long seatId : seatIds) {
            stringRedisTemplate.delete(coordinator.buildKey(eventId, seatId));
        }
    }

    @Test
    @DisplayName("Should successfully acquire multi-seat hold when all keys are available")
    void shouldAcquireMultiSeatHoldSuccessfully() {
        String holdToken = UUID.randomUUID().toString();

        boolean acquired = coordinator.acquireMultiSeatHold(eventId, seatIds, holdToken, 300);

        assertTrue(acquired, "All seats should be acquired");
        for (Long seatId : seatIds) {
            assertEquals(holdToken, coordinator.getHoldToken(eventId, seatId));
        }
    }

    @Test
    @DisplayName("Should reject acquisition atomically if any seat is already held (All-or-Nothing)")
    void shouldRejectAcquisitionIfAnySeatAlreadyHeld() {
        String existingToken = UUID.randomUUID().toString();
        // Pre-lock seat 102
        stringRedisTemplate.opsForValue().set(coordinator.buildKey(eventId, 102L), existingToken);

        String newToken = UUID.randomUUID().toString();
        boolean acquired = coordinator.acquireMultiSeatHold(eventId, seatIds, newToken, 300);

        assertFalse(acquired, "Acquisition must fail because seat 102 is already held");
        // Verify seat 101 and 103 were NOT set
        assertNull(coordinator.getHoldToken(eventId, 101L), "Seat 101 should not be locked");
        assertNull(coordinator.getHoldToken(eventId, 103L), "Seat 103 should not be locked");
        assertEquals(existingToken, coordinator.getHoldToken(eventId, 102L), "Seat 102 should retain original token");
    }

    @Test
    @DisplayName("Compare-and-Delete (CAD) should only release keys matching the exact expected token")
    void shouldReleaseOnlyMatchingTokensViaCad() {
        String tokenUserA = UUID.randomUUID().toString();
        coordinator.acquireMultiSeatHold(eventId, Arrays.asList(101L, 102L), tokenUserA, 300);

        // Attempt CAD release with wrong token
        long releasedWrong = coordinator.releaseMultiSeatHold(eventId, Arrays.asList(101L, 102L), "wrong-token-xyz");
        assertEquals(0, releasedWrong, "Wrong token must not release any keys");
        assertEquals(tokenUserA, coordinator.getHoldToken(eventId, 101L), "Seat 101 key must still exist");

        // Release with correct token
        long releasedCorrect = coordinator.releaseMultiSeatHold(eventId, Arrays.asList(101L, 102L), tokenUserA);
        assertEquals(2, releasedCorrect, "Both keys should be released with matching token");
        assertNull(coordinator.getHoldToken(eventId, 101L));
        assertNull(coordinator.getHoldToken(eventId, 102L));
    }

    @Test
    @DisplayName("Single seat CAD should delete only if token matches and return 1, else 0")
    void shouldExecuteSingleSeatCadSafely() {
        String token = UUID.randomUUID().toString();
        coordinator.acquireMultiSeatHold(eventId, Collections.singletonList(101L), token, 300);

        boolean wrongRelease = coordinator.releaseSingleSeatHold(eventId, 101L, "invalid-token");
        assertFalse(wrongRelease);
        assertTrue(coordinator.isSeatHeld(eventId, 101L));

        boolean correctRelease = coordinator.releaseSingleSeatHold(eventId, 101L, token);
        assertTrue(correctRelease);
        assertFalse(coordinator.isSeatHeld(eventId, 101L));
    }
}

