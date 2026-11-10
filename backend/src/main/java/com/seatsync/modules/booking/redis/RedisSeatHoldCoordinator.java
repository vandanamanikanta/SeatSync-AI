package com.seatsync.modules.booking.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * High-speed Redis concurrency coordinator managing ephemeral seat holds using atomic Lua scripts.
 * Implements Compare-And-Delete (CAD) to guarantee zero blind deletions and safe compensation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSeatHoldCoordinator {

    private static final String KEY_PREFIX = "seat:hold:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> multiSeatHoldScript;
    private final RedisScript<Long> multiSeatCadScript;
    private final RedisScript<Long> singleSeatCadScript;

    public String buildKey(Long eventId, Long seatId) {
        return KEY_PREFIX + eventId + ":" + seatId;
    }

    public List<String> buildKeys(Long eventId, Collection<Long> seatIds) {
        return seatIds.stream()
                .map(seatId -> buildKey(eventId, seatId))
                .toList();
    }

    /**
     * Atomically acquires holds for all specified seats using Lua.
     * All-or-nothing: if ANY seat key already exists in Redis, acquisition fails and returns false.
     *
     * @param eventId    target event
     * @param seatIds    collection of event seat IDs
     * @param holdToken  unique token identifying this reservation session
     * @param ttlSeconds hold duration in seconds (typically 300)
     * @return true if all seats successfully locked; false if any seat is already held
     */
    public boolean acquireMultiSeatHold(Long eventId, Collection<Long> seatIds, String holdToken, int ttlSeconds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return true;
        }

        List<String> keys = buildKeys(eventId, seatIds);
        try {
            Long result = stringRedisTemplate.execute(
                    multiSeatHoldScript,
                    keys,
                    holdToken,
                    String.valueOf(ttlSeconds)
            );
            boolean acquired = result != null && result == 1L;
            if (acquired) {
                log.debug("Redis acquired hold for event {} seats {} with token {} (TTL {}s)",
                        eventId, seatIds, holdToken, ttlSeconds);
            } else {
                log.debug("Redis hold rejected for event {} seats {} - one or more keys already held",
                        eventId, seatIds);
            }
            return acquired;
        } catch (Exception e) {
            log.error("Redis execution failed during multi-seat hold acquisition for event {} seats {}: {}",
                    eventId, seatIds, e.getMessage(), e);
            throw new IllegalStateException("Redis concurrency coordinator failure during hold acquisition", e);
        }
    }

    /**
     * Atomically releases held seats using Compare-and-Delete (CAD) Lua script.
     * Strictly verifies that the key currently matches expectedHoldToken before deleting.
     *
     * @param eventId           target event
     * @param seatIds           collection of event seat IDs to release
     * @param expectedHoldToken token expected to be stored in Redis
     * @return number of keys successfully verified and released
     */
    public long releaseMultiSeatHold(Long eventId, Collection<Long> seatIds, String expectedHoldToken) {
        if (seatIds == null || seatIds.isEmpty() || expectedHoldToken == null) {
            return 0L;
        }

        List<String> keys = buildKeys(eventId, seatIds);
        try {
            Long releasedCount = stringRedisTemplate.execute(
                    multiSeatCadScript,
                    keys,
                    expectedHoldToken
            );
            log.debug("Redis CAD released {}/{} seats for event {} with token {}",
                    releasedCount, keys.size(), eventId, expectedHoldToken);
            return releasedCount != null ? releasedCount : 0L;
        } catch (Exception e) {
            log.error("Redis CAD failed for event {} with token {}: {}",
                    eventId, expectedHoldToken, e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Atomically releases a single seat using Compare-and-Delete (CAD) Lua script.
     *
     * @param eventId           target event
     * @param seatId            event seat ID
     * @param expectedHoldToken token expected to be stored in Redis
     * @return true if key existed, matched token, and was deleted; false otherwise
     */
    public boolean releaseSingleSeatHold(Long eventId, Long seatId, String expectedHoldToken) {
        if (expectedHoldToken == null) {
            return false;
        }

        String key = buildKey(eventId, seatId);
        try {
            Long result = stringRedisTemplate.execute(
                    singleSeatCadScript,
                    Collections.singletonList(key),
                    expectedHoldToken
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Redis single CAD failed for key {} with token {}: {}",
                    key, expectedHoldToken, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Inspects the current token stored in Redis for a seat.
     */
    public String getHoldToken(Long eventId, Long seatId) {
        try {
            return stringRedisTemplate.opsForValue().get(buildKey(eventId, seatId));
        } catch (Exception e) {
            log.warn("Failed to read Redis hold key for event {} seat {}: {}", eventId, seatId, e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a seat key is currently present in Redis.
     */
    public boolean isSeatHeld(Long eventId, Long seatId) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildKey(eventId, seatId)));
        } catch (Exception e) {
            log.warn("Failed to check Redis key existence for event {} seat {}: {}", eventId, seatId, e.getMessage());
            return false;
        }
    }
}

