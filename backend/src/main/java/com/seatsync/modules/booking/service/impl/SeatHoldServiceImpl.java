package com.seatsync.modules.booking.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.common.exception.BadRequestException;
import com.seatsync.common.exception.ForbiddenException;
import com.seatsync.common.exception.ResourceNotFoundException;
import com.seatsync.common.exception.SeatHoldConflictException;
import com.seatsync.modules.booking.dto.request.SeatHoldRequest;
import com.seatsync.modules.booking.dto.request.SeatReleaseRequest;
import com.seatsync.modules.booking.dto.response.SeatHoldResponse;
import com.seatsync.modules.booking.dto.response.SeatReleaseResponse;
import com.seatsync.modules.booking.entity.EventSeat;
import com.seatsync.modules.booking.entity.OutboxEvent;
import com.seatsync.modules.booking.entity.OutboxStatus;
import com.seatsync.modules.booking.entity.SeatStatus;
import com.seatsync.modules.booking.redis.RedisSeatHoldCoordinator;
import com.seatsync.modules.booking.repository.EventSeatRepository;
import com.seatsync.modules.booking.repository.OutboxEventRepository;
import com.seatsync.modules.booking.service.SeatHoldService;
import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventStatus;
import com.seatsync.modules.event.repository.EventRepository;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * High-concurrency Seat Hold Service coordinating fast Redis ephemeral locks
 * and authoritative MySQL transactional persistence with zero double-booking tolerance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldServiceImpl implements SeatHoldService {

    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final UserRepository userRepository;
    private final RedisSeatHoldCoordinator redisSeatHoldCoordinator;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.hold.duration-seconds:300}")
    private int holdDurationSeconds = 300;

    @Override
    public SeatHoldResponse holdSeats(SeatHoldRequest request, Long authenticatedUserId) {
        Long eventId = request.getEventId();
        List<Long> seatIds = request.getResolvedSeatIds();

        if (seatIds.isEmpty()) {
            throw new BadRequestException("At least one seat ID must be specified for hold reservation");
        }

        // Deduplicate and validate seat count
        Set<Long> uniqueSeatIds = new HashSet<>(seatIds);
        if (uniqueSeatIds.size() != seatIds.size()) {
            throw new BadRequestException("Duplicate seat IDs found in hold request");
        }
        if (seatIds.size() > 10) {
            throw new BadRequestException("Cannot hold more than 10 seats in a single reservation");
        }

        // Validate Event exists and is eligible for booking
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new BadRequestException("Cannot hold seats for an event that is " + event.getStatus());
        }

        // Generate unique UUID hold token for this reservation session
        String holdToken = UUID.randomUUID().toString();
        Instant now = Instant.now();

        // -------------------------------------------------------------
        // Step 1: Redis Atomic Multi-Seat Hold Acquisition
        // -------------------------------------------------------------
        boolean redisAcquired = redisSeatHoldCoordinator.acquireMultiSeatHold(
                eventId,
                seatIds,
                holdToken,
                holdDurationSeconds
        );

        if (!redisAcquired) {
            log.warn("Redis hold rejected: One or more seats in {} for event {} are currently held", seatIds, eventId);
            throw new SeatHoldConflictException(
                    "One or more requested seats are currently held by another user",
                    seatIds
            );
        }

        // -------------------------------------------------------------
        // Step 2: MySQL Authoritative Transactional Update
        // -------------------------------------------------------------
        try {
            return transactionTemplate.execute(status -> {
                User user = userRepository.findById(authenticatedUserId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", authenticatedUserId));

                // Lock the requested event seats with SELECT ... FOR UPDATE
                List<EventSeat> eventSeats = eventSeatRepository.findByIdInAndEventIdForUpdate(seatIds, eventId);

                // Verify that all requested seat IDs exist and belong to this event
                if (eventSeats.size() != seatIds.size()) {
                    Set<Long> foundIds = eventSeats.stream().map(EventSeat::getId).collect(Collectors.toSet());
                    List<Long> missingIds = seatIds.stream().filter(id -> !foundIds.contains(id)).toList();
                    throw new BadRequestException("One or more seat IDs do not belong to event " + eventId + ": " + missingIds);
                }

                // Check availability under database row lock
                List<Long> unavailableSeatIds = new ArrayList<>();
                for (EventSeat es : eventSeats) {
                    if (!es.isAvailableForHold(now)) {
                        unavailableSeatIds.add(es.getId());
                    }
                }

                if (!unavailableSeatIds.isEmpty()) {
                    log.warn("Database hold conflict: Seats {} for event {} are unavailable", unavailableSeatIds, eventId);
                    throw new SeatHoldConflictException(
                            "One or more requested seats are no longer available in the database",
                            unavailableSeatIds
                    );
                }

                // Apply hold state to all requested seats
                Instant expiresAt = now.plusSeconds(holdDurationSeconds);
                for (EventSeat es : eventSeats) {
                    es.setStatus(SeatStatus.HELD);
                    es.setHeldByUser(user);
                    es.setHeldUntil(expiresAt);
                    es.setHoldToken(holdToken);
                }

                eventSeatRepository.saveAll(eventSeats);

                // Transactional Outbox: Record SEAT_HELD event in the same MySQL transaction
                Map<String, Object> outboxPayload = new LinkedHashMap<>();
                outboxPayload.put("eventId", eventId);
                outboxPayload.put("seatIds", seatIds);
                outboxPayload.put("status", "HELD");
                outboxPayload.put("heldUntil", expiresAt.toString());
                outboxPayload.put("holdToken", holdToken);
                outboxPayload.put("userId", authenticatedUserId);
                outboxPayload.put("timestamp", now.toString());

                try {
                    String payloadJson = objectMapper.writeValueAsString(outboxPayload);
                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .aggregateType("SEAT")
                            .aggregateId(eventId.toString())
                            .eventType("SEAT_HELD")
                            .payload(payloadJson)
                            .status(OutboxStatus.PENDING)
                            .retryCount(0)
                            .nextAttemptAt(now)
                            .build();
                    outboxEventRepository.save(outboxEvent);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Failed to serialize outbox event for SEAT_HELD", e);
                }

                log.info("Successfully acquired hold for event {} seats {} with token {} (expires at {})",
                        eventId, seatIds, holdToken, expiresAt);

                return SeatHoldResponse.builder()
                        .holdToken(holdToken)
                        .eventId(eventId)
                        .heldSeatIds(seatIds)
                        .expiresAt(expiresAt)
                        .ttlSecondsRemaining(holdDurationSeconds)
                        .build();
            });
        } catch (Exception ex) {
            // -------------------------------------------------------------
            // Step 3: Immediate Compensation on MySQL Failure
            // -------------------------------------------------------------
            log.error("MySQL hold update failed for event {} seats {} with token {}: {}. Triggering Redis compensation CAD...",
                    eventId, seatIds, holdToken, ex.getMessage());
            redisSeatHoldCoordinator.releaseMultiSeatHold(eventId, seatIds, holdToken);
            throw ex;
        }
    }

    @Override
    public SeatReleaseResponse releaseSeats(SeatReleaseRequest request, Long authenticatedUserId, boolean isAdmin) {
        Long eventId = request.getEventId();
        String holdToken = request.getHoldToken();
        List<Long> seatIds = request.getResolvedSeatIds();

        if (holdToken == null || holdToken.trim().isEmpty()) {
            throw new BadRequestException("Hold token is required to release seats");
        }

        // -------------------------------------------------------------
        // Step 1: MySQL Authoritative Hold Release Under Row Lock
        // -------------------------------------------------------------
        List<Long> releasedSeatIds = transactionTemplate.execute(status -> {
            List<EventSeat> eventSeats;
            if (seatIds.isEmpty()) {
                eventSeats = eventSeatRepository.findByEventIdAndHoldTokenForUpdate(eventId, holdToken);
            } else {
                eventSeats = eventSeatRepository.findByEventIdAndHoldTokenAndIdInForUpdate(eventId, holdToken, seatIds);
            }

            if (eventSeats.isEmpty()) {
                throw new ResourceNotFoundException("No active seat holds found matching event " + eventId + " and hold token");
            }

            // Verify hold ownership unless user has ADMIN role
            if (!isAdmin) {
                for (EventSeat es : eventSeats) {
                    if (es.getHeldByUser() != null && !es.getHeldByUser().getId().equals(authenticatedUserId)) {
                        throw new ForbiddenException("You do not have permission to release seat " + es.getId() + " held by another user");
                    }
                }
            }

            List<Long> released = new ArrayList<>();
            for (EventSeat es : eventSeats) {
                es.setStatus(SeatStatus.AVAILABLE);
                es.setHeldByUser(null);
                es.setHeldUntil(null);
                es.setHoldToken(null);
                released.add(es.getId());
            }

            eventSeatRepository.saveAll(eventSeats);

            // Transactional Outbox: Record SEAT_RELEASED event in the same MySQL transaction
            Map<String, Object> outboxPayload = new LinkedHashMap<>();
            outboxPayload.put("eventId", eventId);
            outboxPayload.put("seatIds", released);
            outboxPayload.put("status", "AVAILABLE");
            outboxPayload.put("holdToken", holdToken);
            outboxPayload.put("timestamp", Instant.now().toString());

            try {
                String payloadJson = objectMapper.writeValueAsString(outboxPayload);
                OutboxEvent outboxEvent = OutboxEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .aggregateType("SEAT")
                        .aggregateId(eventId.toString())
                        .eventType("SEAT_RELEASED")
                        .payload(payloadJson)
                        .status(OutboxStatus.PENDING)
                        .retryCount(0)
                        .nextAttemptAt(Instant.now())
                        .build();
                outboxEventRepository.save(outboxEvent);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize outbox event for SEAT_RELEASED", e);
            }

            log.info("Released {} seats for event {} with hold token {} in MySQL", released.size(), eventId, holdToken);
            return released;
        });

        // -------------------------------------------------------------
        // Step 2: Atomic Compare-and-Delete (CAD) in Redis
        // -------------------------------------------------------------
        if (releasedSeatIds != null && !releasedSeatIds.isEmpty()) {
            redisSeatHoldCoordinator.releaseMultiSeatHold(eventId, releasedSeatIds, holdToken);
        }

        return SeatReleaseResponse.builder()
                .eventId(eventId)
                .releasedSeatIds(releasedSeatIds)
                .releasedCount(releasedSeatIds != null ? releasedSeatIds.size() : 0)
                .build();
    }

    @Override
    @Scheduled(fixedDelayString = "${app.hold.reconciliation-interval-ms:15000}")
    public int reconcileExpiredHolds() {
        Instant now = Instant.now();
        List<EventSeat> expiredSeats = eventSeatRepository.findExpiredHolds(now);
        if (expiredSeats.isEmpty()) {
            return 0;
        }

        log.debug("Found {} candidate expired seat holds to reconcile", expiredSeats.size());
        int cleanedCount = 0;

        for (EventSeat candidate : expiredSeats) {
            Long seatId = candidate.getId();
            Long eventId = candidate.getEvent().getId();
            String expiredToken = candidate.getHoldToken();

            boolean reset = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                Optional<EventSeat> opt = eventSeatRepository.findByIdForUpdate(seatId);
                if (opt.isPresent()) {
                    EventSeat es = opt.get();
                    if (es.getStatus() == SeatStatus.HELD && es.isHoldExpired(Instant.now()) && es.getCurrentBooking() == null) {
                        es.setStatus(SeatStatus.AVAILABLE);
                        es.setHeldByUser(null);
                        es.setHeldUntil(null);
                        es.setHoldToken(null);
                        eventSeatRepository.save(es);

                        // Transactional Outbox: Record expired SEAT_RELEASED event
                        Map<String, Object> outboxPayload = new LinkedHashMap<>();
                        outboxPayload.put("eventId", eventId);
                        outboxPayload.put("seatIds", List.of(seatId));
                        outboxPayload.put("status", "AVAILABLE");
                        outboxPayload.put("reason", "EXPIRED");
                        outboxPayload.put("timestamp", Instant.now().toString());

                        try {
                            String payloadJson = objectMapper.writeValueAsString(outboxPayload);
                            OutboxEvent outboxEvent = OutboxEvent.builder()
                                    .eventId(UUID.randomUUID().toString())
                                    .aggregateType("SEAT")
                                    .aggregateId(eventId.toString())
                                    .eventType("SEAT_RELEASED")
                                    .payload(payloadJson)
                                    .status(OutboxStatus.PENDING)
                                    .retryCount(0)
                                    .nextAttemptAt(Instant.now())
                                    .build();
                            outboxEventRepository.save(outboxEvent);
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize outbox event for expired seat {}: {}", seatId, e.getMessage());
                        }

                        return true;
                    }
                }
                return false;
            }));

            if (reset) {
                cleanedCount++;
                if (expiredToken != null) {
                    redisSeatHoldCoordinator.releaseSingleSeatHold(eventId, seatId, expiredToken);
                }
            }
        }

        if (cleanedCount > 0) {
            log.info("Reconciled and released {} expired seat holds", cleanedCount);
        }
        return cleanedCount;
    }
}

