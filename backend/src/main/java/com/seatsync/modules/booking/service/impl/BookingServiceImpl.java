package com.seatsync.modules.booking.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.common.exception.BadRequestException;
import com.seatsync.common.exception.ForbiddenException;
import com.seatsync.common.exception.ResourceNotFoundException;
import com.seatsync.common.exception.SeatHoldConflictException;
import com.seatsync.modules.booking.dto.request.CreateBookingRequest;
import com.seatsync.modules.booking.dto.response.BookingResponse;
import com.seatsync.modules.booking.entity.*;
import com.seatsync.modules.booking.payment.MockPaymentFacade;
import com.seatsync.modules.booking.payment.MockPaymentResult;
import com.seatsync.modules.booking.redis.RedisSeatHoldCoordinator;
import com.seatsync.modules.booking.repository.BookingItemRepository;
import com.seatsync.modules.booking.repository.BookingRepository;
import com.seatsync.modules.booking.repository.EventSeatRepository;
import com.seatsync.modules.booking.repository.OutboxEventRepository;
import com.seatsync.modules.booking.service.BookingService;
import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventStatus;
import com.seatsync.modules.event.entity.Seat;
import com.seatsync.modules.event.repository.EventRepository;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production-grade Booking Checkout and Confirmation Engine.
 * Enforces strict transactional integrity, atomic seat confirmation,
 * idempotent replay, mock payment processing, and transactional outbox event publishing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RedisSeatHoldCoordinator redisCoordinator;
    private final MockPaymentFacade mockPaymentFacade;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public BookingResponse createBooking(CreateBookingRequest request, Long authenticatedUserId) {
        String idempotencyKey = request.getIdempotencyKey().trim();

        // -------------------------------------------------------------
        // Step 1: Idempotency Check (Safe Idempotent Replay)
        // -------------------------------------------------------------
        Optional<Booking> existingOpt = bookingRepository.findByUserIdAndIdempotencyKeyWithEventDetails(authenticatedUserId, idempotencyKey);
        if (existingOpt.isPresent()) {
            log.info("Idempotent booking hit detected for user {} with key {}. Returning existing booking.",
                    authenticatedUserId, idempotencyKey);
            return mapToBookingResponse(existingOpt.get());
        }

        // -------------------------------------------------------------
        // Step 2: Input Validation
        // -------------------------------------------------------------
        Long eventId = request.getEventId();
        List<Long> seatIds = request.getResolvedSeatIds();

        if (seatIds.isEmpty()) {
            throw new BadRequestException("At least one seat must be selected for booking");
        }

        Set<Long> uniqueSeatIds = new HashSet<>(seatIds);
        if (uniqueSeatIds.size() != seatIds.size()) {
            throw new BadRequestException("Duplicate seat IDs found in booking request");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new BadRequestException("Cannot book seats for an event that is " + event.getStatus());
        }

        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", authenticatedUserId));

        String holdToken = request.getHoldToken().trim();
        Instant now = Instant.now();

        // -------------------------------------------------------------
        // Step 3: Transactional Execution (MySQL ACID Boundary)
        // -------------------------------------------------------------
        Booking committedBooking;
        try {
            committedBooking = transactionTemplate.execute(status -> {
                // 3.1 Lock and verify target event seats under row lock
                List<EventSeat> eventSeats = eventSeatRepository.findByIdInAndEventIdForUpdate(seatIds, eventId);

                if (eventSeats.size() != seatIds.size()) {
                    Set<Long> foundIds = eventSeats.stream().map(EventSeat::getId).collect(Collectors.toSet());
                    List<Long> missingIds = seatIds.stream().filter(id -> !foundIds.contains(id)).toList();
                    throw new BadRequestException("One or more seat IDs do not belong to event " + eventId + ": " + missingIds);
                }

                // 3.2 Validate each seat hold status, ownership, token, and expiration
                for (EventSeat es : eventSeats) {
                    if (es.getStatus() != SeatStatus.HELD) {
                        throw new SeatHoldConflictException("Seat " + es.getId() + " is not currently held (status: " + es.getStatus() + ")");
                    }
                    if (es.getHeldByUser() == null || !es.getHeldByUser().getId().equals(authenticatedUserId)) {
                        throw new ForbiddenException("Seat " + es.getId() + " is held by another user");
                    }
                    if (es.getHeldUntil() == null || !es.getHeldUntil().isAfter(now)) {
                        throw new SeatHoldConflictException("Hold on seat " + es.getId() + " has expired");
                    }
                    if (es.getHoldToken() == null || !es.getHoldToken().equals(holdToken)) {
                        throw new BadRequestException("Hold token mismatch for seat " + es.getId());
                    }
                    if (es.getCurrentBooking() != null) {
                        throw new SeatHoldConflictException("Seat " + es.getId() + " is already assigned to a booking");
                    }
                }

                // 3.3 Compute total booking amount
                BigDecimal totalAmount = eventSeats.stream()
                        .map(EventSeat::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 3.4 Create PENDING booking
                String bookingRef = "SS-2026-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                Booking booking = Booking.builder()
                        .bookingReference(bookingRef)
                        .user(user)
                        .event(event)
                        .totalAmount(totalAmount)
                        .currency("INR")
                        .status(BookingStatus.PENDING)
                        .idempotencyKey(idempotencyKey)
                        .build();

                booking = bookingRepository.save(booking);

                // 3.5 Conditionally update seats to BOOKED via atomic CAS query
                int updatedCount = eventSeatRepository.confirmSeatsForBooking(
                        seatIds,
                        eventId,
                        booking,
                        authenticatedUserId,
                        holdToken,
                        now
                );

                if (updatedCount != seatIds.size()) {
                    log.error("CAS seat confirmation mismatch: Expected {} rows, updated {} rows",
                            seatIds.size(), updatedCount);
                    throw new SeatHoldConflictException(
                            "One or more seat holds have expired or were modified. Rolling back entire booking."
                    );
                }

                // 3.6 Insert booking line items
                List<BookingItem> items = new ArrayList<>();
                for (EventSeat es : eventSeats) {
                    items.add(BookingItem.builder()
                            .booking(booking)
                            .eventSeat(es)
                            .unitPrice(es.getPrice())
                            .status(BookingItemStatus.CONFIRMED)
                            .build());
                }
                bookingItemRepository.saveAll(items);

                // 3.7 Process Mock Payment
                MockPaymentResult paymentResult = mockPaymentFacade.processPayment(booking, request.getPaymentMethod());
                if (!paymentResult.isSuccess()) {
                    throw new com.seatsync.common.exception.PaymentFailedException(
                            "Mock payment failed: " + paymentResult.getErrorMessage()
                    );
                }

                // Transition booking status to CONFIRMED
                booking.setStatus(BookingStatus.CONFIRMED);
                booking = bookingRepository.save(booking);

                // 3.8 Insert Transactional Outbox Event
                Map<String, Object> outboxPayload = new LinkedHashMap<>();
                outboxPayload.put("bookingId", booking.getId());
                outboxPayload.put("bookingReference", booking.getBookingReference());
                outboxPayload.put("userId", authenticatedUserId);
                outboxPayload.put("eventId", eventId);
                outboxPayload.put("totalAmount", booking.getTotalAmount());
                outboxPayload.put("seatIds", seatIds);
                outboxPayload.put("confirmedAt", Instant.now().toString());

                String payloadJson;
                try {
                    payloadJson = objectMapper.writeValueAsString(outboxPayload);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Failed to serialize outbox event payload", e);
                }

                OutboxEvent outboxEvent = OutboxEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .aggregateType("BOOKING")
                        .aggregateId(booking.getId().toString())
                        .eventType("BOOKING_CONFIRMED")
                        .payload(payloadJson)
                        .status(OutboxStatus.PENDING)
                        .retryCount(0)
                        .nextAttemptAt(Instant.now())
                        .build();

                outboxEventRepository.save(outboxEvent);

                log.info("Booking {} ({}) successfully committed for user {} on event {}",
                        booking.getId(), booking.getBookingReference(), authenticatedUserId, eventId);

                return booking;
            });
        } catch (Exception ex) {
            // Concurrent idempotent race condition - check if another thread already committed this idempotency key
            Optional<Booking> concurrentOpt = bookingRepository.findByUserIdAndIdempotencyKeyWithEventDetails(authenticatedUserId, idempotencyKey);
            if (concurrentOpt.isPresent()) {
                log.info("Concurrent idempotent race detected for user {} with key {}. Returning existing committed booking.",
                        authenticatedUserId, idempotencyKey);
                return mapToBookingResponse(concurrentOpt.get());
            }
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Booking transaction failed", ex);
        }

        // -------------------------------------------------------------
        // Step 4: Post-Commit Redis CAD Cleanup
        // -------------------------------------------------------------
        try {
            redisCoordinator.releaseMultiSeatHold(eventId, seatIds, holdToken);
            log.debug("Post-commit Redis hold keys successfully released via CAD for event {} seats {}", eventId, seatIds);
        } catch (Exception e) {
            // A Redis failure post-commit must NEVER undo an already committed booking!
            log.warn("Post-commit Redis hold cleanup failed for event {} seats {} with token {}: {}. Booking remains authoritative and committed.",
                    eventId, seatIds, holdToken, e.getMessage());
        }

        return mapToBookingResponse(committedBooking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(Long authenticatedUserId) {
        List<Booking> bookings = bookingRepository.findByUserIdWithEventDetails(authenticatedUserId);
        return bookings.stream()
                .map(this::mapToBookingResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long bookingId, Long authenticatedUserId, boolean isAdmin) {
        Booking booking = bookingRepository.findByIdWithEventDetails(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));

        if (!isAdmin && !booking.getUser().getId().equals(authenticatedUserId)) {
            throw new ForbiddenException("You do not have permission to access booking " + bookingId);
        }

        return mapToBookingResponse(booking);
    }

    private BookingResponse mapToBookingResponse(Booking booking) {
        Booking fullBooking = bookingRepository.findByIdWithEventDetails(booking.getId())
                .orElse(booking);

        List<BookingItem> items = bookingItemRepository.findByBookingIdWithDetails(fullBooking.getId());

        List<BookingResponse.BookingItemDto> itemDtos = items.stream().map(bi -> {
            EventSeat es = bi.getEventSeat();
            Seat s = es.getSeat();
            return BookingResponse.BookingItemDto.builder()
                    .itemId(bi.getId())
                    .eventSeatId(es.getId())
                    .seatId(s.getId())
                    .sectionName(s.getVenueSection().getName())
                    .rowLabel(s.getRowLabel())
                    .seatNumber(s.getSeatNumber())
                    .unitPrice(bi.getUnitPrice())
                    .status(bi.getStatus().name())
                    .build();
        }).toList();

        return BookingResponse.builder()
                .bookingId(fullBooking.getId())
                .bookingReference(fullBooking.getBookingReference())
                .eventId(fullBooking.getEvent().getId())
                .eventTitle(fullBooking.getEvent().getTitle())
                .venueName(fullBooking.getEvent().getVenue().getName())
                .userId(fullBooking.getUser().getId())
                .customerEmail(fullBooking.getUser().getEmail())
                .totalAmount(fullBooking.getTotalAmount())
                .currency(fullBooking.getCurrency())
                .status(fullBooking.getStatus().name())
                .idempotencyKey(fullBooking.getIdempotencyKey())
                .createdAt(fullBooking.getCreatedAt())
                .items(itemDtos)
                .build();
    }
}

