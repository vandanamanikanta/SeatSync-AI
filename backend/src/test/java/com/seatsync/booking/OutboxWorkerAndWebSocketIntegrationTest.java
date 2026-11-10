package com.seatsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.common.security.JwtTokenProvider;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.common.security.WebSocketChannelInterceptor;
import com.seatsync.modules.booking.dto.request.CreateBookingRequest;
import com.seatsync.modules.booking.dto.request.SeatHoldRequest;
import com.seatsync.modules.booking.dto.request.SeatReleaseRequest;
import com.seatsync.modules.booking.dto.response.BookingResponse;
import com.seatsync.modules.booking.dto.response.SeatHoldResponse;
import com.seatsync.modules.booking.entity.EventSeat;
import com.seatsync.modules.booking.entity.OutboxEvent;
import com.seatsync.modules.booking.entity.OutboxStatus;
import com.seatsync.modules.booking.entity.SeatStatus;
import com.seatsync.modules.booking.outbox.OutboxMessageDispatcher;
import com.seatsync.modules.booking.outbox.OutboxWorker;
import com.seatsync.modules.booking.outbox.SeatUpdateMessage;
import com.seatsync.modules.booking.redis.RedisSeatHoldCoordinator;
import com.seatsync.modules.booking.repository.BookingItemRepository;
import com.seatsync.modules.booking.repository.BookingRepository;
import com.seatsync.modules.booking.repository.EventSeatRepository;
import com.seatsync.modules.booking.repository.OutboxEventRepository;
import com.seatsync.modules.booking.service.BookingService;
import com.seatsync.modules.booking.service.SeatHoldService;
import com.seatsync.modules.event.entity.*;
import com.seatsync.modules.event.repository.EventCategoryRepository;
import com.seatsync.modules.event.repository.EventRepository;
import com.seatsync.modules.event.repository.SeatRepository;
import com.seatsync.modules.event.repository.VenueRepository;
import com.seatsync.modules.event.repository.VenueSectionRepository;
import com.seatsync.modules.user.entity.Role;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.entity.UserStatus;
import com.seatsync.modules.user.repository.RoleRepository;
import com.seatsync.modules.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties = {
        "seatsync.outbox.poll-interval-ms=60000", // Prevent automatic background polling during test assertions
        "seatsync.outbox.batch-size=50",
        "seatsync.outbox.lease-duration-seconds=30",
        "seatsync.outbox.max-retries=3"
})
class OutboxWorkerAndWebSocketIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxWorker outboxWorker;

    @SpyBean
    private OutboxMessageDispatcher messageDispatcher;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RedisSeatHoldCoordinator redisCoordinator;

    @Autowired
    private EventSeatRepository eventSeatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private VenueSectionRepository venueSectionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventCategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private WebSocketChannelInterceptor webSocketChannelInterceptor;

    @Autowired
    private ObjectMapper objectMapper;

    private User testCustomer;
    private String customerJwt;
    private Event testEvent;
    private EventSeat seat1;
    private EventSeat seat2;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        bookingItemRepository.deleteAll();
        eventSeatRepository.deleteAll();
        bookingRepository.deleteAll();
        seatRepository.deleteAll();
        venueSectionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role customerRole = roleRepository.save(new Role("ROLE_CUSTOMER", "Customer"));
        testCustomer = userRepository.save(User.builder()
                .email("outbox.tester@seatsync.ai")
                .passwordHash("hashed")
                .fullName("Outbox Tester")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build());

        customerJwt = jwtTokenProvider.generateAccessToken(UserPrincipal.create(testCustomer));

        Venue venue = venueRepository.save(Venue.builder()
                .name("Grand Outbox Arena")
                .slug("grand-outbox-arena-" + UUID.randomUUID().toString().substring(0, 6))
                .city("Mumbai")
                .state("Maharashtra")
                .postalCode("400001")
                .address("100 Tech Blvd")
                .capacity(200)
                .build());

        VenueSection section = venueSectionRepository.save(VenueSection.builder()
                .venue(venue)
                .name("VIP A")
                .sectionCode("VIP")
                .tierLevel(1)
                .capacity(100)
                .displayOrder(1)
                .build());

        Seat physSeat1 = seatRepository.save(Seat.builder()
                .venueSection(section)
                .rowLabel("A")
                .seatNumber(1)
                .gridX(1)
                .gridY(1)
                .build());

        Seat physSeat2 = seatRepository.save(Seat.builder()
                .venueSection(section)
                .rowLabel("A")
                .seatNumber(2)
                .gridX(2)
                .gridY(1)
                .build());

        EventCategory category = categoryRepository.save(EventCategory.builder()
                .name("Concerts")
                .slug("concerts-" + UUID.randomUUID().toString().substring(0, 6))
                .build());

        testEvent = eventRepository.save(Event.builder()
                .title("Symphony Live 2026")
                .slug("symphony-live-" + UUID.randomUUID().toString().substring(0, 6))
                .shortDescription("Live orchestra performance")
                .category(category)
                .venue(venue)
                .organizer(testCustomer)
                .startTime(Instant.now().plus(5, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(5, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .basePrice(new BigDecimal("2000.00"))
                .status(EventStatus.PUBLISHED)
                .build());

        seat1 = eventSeatRepository.save(EventSeat.builder()
                .event(testEvent)
                .seat(physSeat1)
                .price(new BigDecimal("2000.00"))
                .status(SeatStatus.AVAILABLE)
                .build());

        seat2 = eventSeatRepository.save(EventSeat.builder()
                .event(testEvent)
                .seat(physSeat2)
                .price(new BigDecimal("2000.00"))
                .status(SeatStatus.AVAILABLE)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (testEvent != null) {
            try {
                redisCoordinator.releaseMultiSeatHold(testEvent.getId(),
                        Arrays.asList(seat1.getId(), seat2.getId()),
                        redisCoordinator.getHoldToken(testEvent.getId(), seat1.getId()));
            } catch (Exception ignored) {
            }
        }
        outboxEventRepository.deleteAll();
        bookingItemRepository.deleteAll();
        eventSeatRepository.deleteAll();
        bookingRepository.deleteAll();
        seatRepository.deleteAll();
        venueSectionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Outbox: AVAILABLE -> HELD generates reliable SEAT_HELD outbox record in same transaction")
    void shouldCreateOutboxEventOnSeatHold() throws Exception {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .build();

        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, testCustomer.getId());
        assertNotNull(holdResp.getHoldToken());

        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertEquals(1, outboxList.size(), "Exactly 1 outbox record should be created on hold");

        OutboxEvent event = outboxList.get(0);
        assertEquals("SEAT", event.getAggregateType());
        assertEquals(testEvent.getId().toString(), event.getAggregateId());
        assertEquals("SEAT_HELD", event.getEventType());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getRetryCount());
        assertNull(event.getClaimToken());

        com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(event.getPayload());
        if (payload.isTextual()) {
            payload = objectMapper.readTree(payload.asText());
        }
        assertEquals(testEvent.getId().intValue(), payload.get("eventId").asInt());
        assertEquals("HELD", payload.get("status").asText());
        assertEquals(holdResp.getHoldToken(), payload.get("holdToken").asText());
    }

    @Test
    @DisplayName("2. Outbox: HELD -> AVAILABLE generates SEAT_RELEASED outbox record on manual release")
    void shouldCreateOutboxEventOnSeatRelease() throws Exception {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, testCustomer.getId());

        outboxEventRepository.deleteAll(); // clear hold event

        SeatReleaseRequest releaseReq = SeatReleaseRequest.builder()
                .eventId(testEvent.getId())
                .holdToken(holdResp.getHoldToken())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .build();

        seatHoldService.releaseSeats(releaseReq, testCustomer.getId(), false);

        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertEquals(1, outboxList.size(), "Exactly 1 outbox record should be created on release");

        OutboxEvent event = outboxList.get(0);
        assertEquals("SEAT_RELEASED", event.getEventType());
        assertEquals(OutboxStatus.PENDING, event.getStatus());

        com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(event.getPayload());
        if (payload.isTextual()) {
            payload = objectMapper.readTree(payload.asText());
        }
        assertEquals(testEvent.getId().intValue(), payload.get("eventId").asInt());
        assertEquals("AVAILABLE", payload.get("status").asText());
    }

    @Test
    @DisplayName("3. Outbox: HELD -> BOOKED generates BOOKING_CONFIRMED outbox record on checkout")
    void shouldCreateOutboxEventOnBookingConfirmation() throws Exception {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, testCustomer.getId());

        outboxEventRepository.deleteAll(); // clear hold event

        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .holdToken(holdResp.getHoldToken())
                .idempotencyKey("outbox-checkout-" + UUID.randomUUID())
                .paymentMethod("MOCK_CARD_VALID")
                .build();

        BookingResponse bookingResp = bookingService.createBooking(checkoutReq, testCustomer.getId());
        assertNotNull(bookingResp.getBookingId());

        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertEquals(1, outboxList.size(), "Exactly 1 outbox record created on booking confirmation");

        OutboxEvent event = outboxList.get(0);
        assertEquals("BOOKING", event.getAggregateType());
        assertEquals(bookingResp.getBookingId().toString(), event.getAggregateId());
        assertEquals("BOOKING_CONFIRMED", event.getEventType());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
    }

    @Test
    @DisplayName("4. Outbox Worker: Claims record, dispatches to WebSocket /topic, and transitions to PUBLISHED")
    void shouldPublishOutboxEventAndBroadcastOverWebSocket() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .build();
        seatHoldService.holdSeats(holdReq, testCustomer.getId());

        assertEquals(1, outboxEventRepository.findByStatus(OutboxStatus.PENDING).size());

        // Run worker synchronously
        int published = outboxWorker.processOutboxEvents();
        assertEquals(1, published, "Worker should publish exactly 1 event");

        // Verify WebSocket message broadcast
        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SeatUpdateMessage> msgCaptor = ArgumentCaptor.forClass(SeatUpdateMessage.class);

        verify(messagingTemplate, times(1)).convertAndSend(destCaptor.capture(), msgCaptor.capture());
        assertEquals("/topic/events/" + testEvent.getId() + "/seats", destCaptor.getValue());

        SeatUpdateMessage msg = msgCaptor.getValue();
        assertEquals(testEvent.getId(), msg.getEventId());
        assertEquals("HELD", msg.getStatus());
        assertEquals(2, msg.getSeatIds().size());

        // Verify database state: status is now PUBLISHED and claimToken is null
        List<OutboxEvent> publishedList = outboxEventRepository.findByStatus(OutboxStatus.PUBLISHED);
        assertEquals(1, publishedList.size());
        assertNull(publishedList.get(0).getClaimToken());
        assertNull(publishedList.get(0).getClaimedAt());
    }

    @Test
    @DisplayName("5. Outbox Worker: Failed publication backs off exponentially without losing record")
    void shouldRetryWithExponentialBackoffOnDispatchFailure() throws Exception {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .build();
        seatHoldService.holdSeats(holdReq, testCustomer.getId());

        // Simulate WebSocket / broker dispatch failure
        doThrow(new RuntimeException("Simulated WebSocket broker connection down"))
                .when(messageDispatcher).dispatch(any(OutboxEvent.class));

        int published = outboxWorker.processOutboxEvents();
        assertEquals(0, published, "No events should be published due to failure");

        List<OutboxEvent> pendingList = outboxEventRepository.findByStatus(OutboxStatus.PENDING);
        assertEquals(1, pendingList.size(), "Event should revert to PENDING for retry");

        OutboxEvent event = pendingList.get(0);
        assertEquals(1, event.getRetryCount(), "Retry count incremented to 1");
        assertNotNull(event.getLastErrorMessage());
        assertTrue(event.getLastErrorMessage().contains("Simulated WebSocket broker connection down"));
        assertTrue(event.getNextAttemptAt().isAfter(Instant.now()), "Next attempt scheduled with backoff in the future");
    }

    @Test
    @DisplayName("6. Outbox Worker: Exceeded max retries transitions event to FAILED for debugging")
    void shouldMarkFailedWhenMaxRetriesExceeded() throws Exception {
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType("SEAT")
                .aggregateId(testEvent.getId().toString())
                .eventType("SEAT_HELD")
                .payload("{\"eventId\":" + testEvent.getId() + ",\"seatIds\":[" + seat1.getId() + "]}")
                .status(OutboxStatus.PENDING)
                .retryCount(2) // max-retries is 3, so next failure reaches 3 (max)
                .nextAttemptAt(Instant.now().minusSeconds(5))
                .build());

        doThrow(new RuntimeException("Fatal broker corruption"))
                .when(messageDispatcher).dispatch(any(OutboxEvent.class));

        int published = outboxWorker.processOutboxEvents();
        assertEquals(0, published);

        OutboxEvent failedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxStatus.FAILED, failedEvent.getStatus(), "Status must transition to FAILED");
        assertEquals(3, failedEvent.getRetryCount());
        assertNull(failedEvent.getClaimToken());
        assertTrue(failedEvent.getLastErrorMessage().contains("Fatal broker corruption"));
    }

    @Test
    @DisplayName("7. Outbox Worker: Expired lease claim recovery allows another worker to reclaim")
    void shouldRecoverExpiredClaim() {
        // Create an event stuck in PROCESSING whose lease has expired (> 30s ago)
        OutboxEvent stalledEvent = outboxEventRepository.save(OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType("SEAT")
                .aggregateId(testEvent.getId().toString())
                .eventType("SEAT_HELD")
                .payload("{\"eventId\":" + testEvent.getId() + ",\"seatIds\":[" + seat1.getId() + "]}")
                .status(OutboxStatus.PROCESSING)
                .claimToken("stale-crashed-worker-token")
                .claimedAt(Instant.now().minusSeconds(60)) // Expired lease
                .nextAttemptAt(Instant.now().minusSeconds(60))
                .retryCount(0)
                .build());

        int published = outboxWorker.processOutboxEvents();
        assertEquals(1, published, "Worker should recover and publish expired lease event");

        OutboxEvent updated = outboxEventRepository.findById(stalledEvent.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, updated.getStatus());
        assertNull(updated.getClaimToken());
    }

    @Test
    @DisplayName("8. WebSocket Security: Authenticates CONNECT frame with valid Bearer token")
    void testWebSocketSecurityConnectWithValidToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer " + customerJwt);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = webSocketChannelInterceptor.preSend(message, channel);
        assertNotNull(result);

        StompHeaderAccessor resAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resAccessor.getUser(), "Security principal must be set on accessor");
        assertEquals("outbox.tester@seatsync.ai", resAccessor.getUser().getName());
    }

    @Test
    @DisplayName("9. WebSocket Security: Rejects CONNECT frame with invalid or missing token")
    void testWebSocketSecurityConnectWithInvalidToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer invalid-tampered-token");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        assertThrows(BadCredentialsException.class, () ->
                webSocketChannelInterceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("10. WebSocket Security: Authorizes SUBSCRIBE to valid /topic/events/{eventId}/seats")
    void testWebSocketSecuritySubscribeValidTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/events/" + testEvent.getId() + "/seats");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        Message<?> result = webSocketChannelInterceptor.preSend(message, channel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("11. WebSocket Security: Rejects SUBSCRIBE to unauthorized or non-existent event topics")
    void testWebSocketSecuritySubscribeInvalidTopic() {
        MessageChannel channel = mock(MessageChannel.class);

        // Subscribing to an unpermitted pattern
        StompHeaderAccessor accessorBadPattern = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessorBadPattern.setDestination("/topic/admin/secret-channel");
        Message<?> msgBad = MessageBuilder.createMessage(new byte[0], accessorBadPattern.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                webSocketChannelInterceptor.preSend(msgBad, channel));

        // Subscribing to non-existent event
        StompHeaderAccessor accessorNonExistent = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessorNonExistent.setDestination("/topic/events/999999/seats");
        Message<?> msgNonExistent = MessageBuilder.createMessage(new byte[0], accessorNonExistent.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                webSocketChannelInterceptor.preSend(msgNonExistent, channel));
    }

    @Test
    @DisplayName("12. WebSocket Security: Strictly rejects inbound SEND frames (read-only updates)")
    void testWebSocketSecuritySendFrameRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/bookSeat");

        Message<?> message = MessageBuilder.createMessage("{\"seatId\":1}".getBytes(), accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                webSocketChannelInterceptor.preSend(message, channel));
        assertTrue(ex.getMessage().contains("Client messages not permitted"));
    }
}
