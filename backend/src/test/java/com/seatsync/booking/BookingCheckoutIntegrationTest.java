package com.seatsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.common.exception.BadRequestException;
import com.seatsync.common.exception.ForbiddenException;
import com.seatsync.common.exception.PaymentFailedException;
import com.seatsync.common.exception.SeatHoldConflictException;
import com.seatsync.common.security.JwtTokenProvider;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.booking.dto.request.CreateBookingRequest;
import com.seatsync.modules.booking.dto.request.SeatHoldRequest;
import com.seatsync.modules.booking.dto.response.BookingResponse;
import com.seatsync.modules.booking.dto.response.SeatHoldResponse;
import com.seatsync.modules.booking.entity.*;
import com.seatsync.modules.booking.redis.RedisSeatHoldCoordinator;
import com.seatsync.modules.booking.repository.BookingItemRepository;
import com.seatsync.modules.booking.repository.BookingRepository;
import com.seatsync.modules.booking.repository.EventSeatRepository;
import com.seatsync.modules.booking.repository.OutboxEventRepository;
import com.seatsync.modules.booking.service.BookingService;
import com.seatsync.modules.booking.service.SeatHoldService;
import com.seatsync.modules.event.entity.*;
import com.seatsync.modules.event.repository.*;
import com.seatsync.modules.user.entity.Role;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.entity.UserStatus;
import com.seatsync.modules.user.repository.RoleRepository;
import com.seatsync.modules.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingCheckoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService seatHoldService;

    @SpyBean
    private RedisSeatHoldCoordinator redisCoordinator;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private EventSeatRepository eventSeatRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private VenueSectionRepository venueSectionRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EventCategoryRepository categoryRepository;

    private User customer1;
    private User customer2;
    private User adminUser;
    private User organizerUser;
    private String customer1Token;
    private String customer2Token;
    private String adminToken;
    private String organizerToken;
    private Event sampleEvent;
    private EventSeat seat1;
    private EventSeat seat2;
    private EventSeat seat3;

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
        Role adminRole = roleRepository.save(new Role("ROLE_ADMIN", "Admin"));
        Role organizerRole = roleRepository.save(new Role("ROLE_ORGANIZER", "Organizer"));

        customer1 = userRepository.save(User.builder()
                .email("cust1@seatsync.ai")
                .passwordHash("hashed")
                .fullName("Customer One")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build());

        customer2 = userRepository.save(User.builder()
                .email("cust2@seatsync.ai")
                .passwordHash("hashed")
                .fullName("Customer Two")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build());

        adminUser = userRepository.save(User.builder()
                .email("admin@seatsync.ai")
                .passwordHash("hashed")
                .fullName("Admin User")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(adminRole))
                .build());

        organizerUser = userRepository.save(User.builder()
                .email("org@seatsync.ai")
                .passwordHash("hashed")
                .fullName("Organizer User")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(organizerRole))
                .build());

        customer1Token = jwtTokenProvider.generateAccessToken(UserPrincipal.create(customer1));
        customer2Token = jwtTokenProvider.generateAccessToken(UserPrincipal.create(customer2));
        adminToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(adminUser));
        organizerToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(organizerUser));

        Venue venue = venueRepository.save(Venue.builder()
                .name("Grand Symphony Hall")
                .slug("grand-symphony-hall")
                .address("456 Orchestra Way")
                .city("Hyderabad")
                .state("Telangana")
                .postalCode("500081")
                .capacity(1000)
                .build());

        VenueSection section = venueSectionRepository.save(VenueSection.builder()
                .venue(venue)
                .name("VIP Center")
                .sectionCode("VIP")
                .tierLevel(1)
                .capacity(50)
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

        Seat physSeat3 = seatRepository.save(Seat.builder()
                .venueSection(section)
                .rowLabel("A")
                .seatNumber(3)
                .gridX(3)
                .gridY(1)
                .build());

        EventCategory category = categoryRepository.save(EventCategory.builder()
                .name("Classical")
                .slug("classical")
                .build());

        sampleEvent = eventRepository.save(Event.builder()
                .title("Mozart Gala 2026")
                .slug("mozart-gala-2026")
                .shortDescription("Classical orchestra performance")
                .category(category)
                .venue(venue)
                .organizer(organizerUser)
                .startTime(Instant.now().plus(10, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(10, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .basePrice(new BigDecimal("2500.00"))
                .status(EventStatus.PUBLISHED)
                .build());

        seat1 = eventSeatRepository.save(EventSeat.builder()
                .event(sampleEvent)
                .seat(physSeat1)
                .price(new BigDecimal("2500.00"))
                .status(SeatStatus.AVAILABLE)
                .build());

        seat2 = eventSeatRepository.save(EventSeat.builder()
                .event(sampleEvent)
                .seat(physSeat2)
                .price(new BigDecimal("2500.00"))
                .status(SeatStatus.AVAILABLE)
                .build());

        seat3 = eventSeatRepository.save(EventSeat.builder()
                .event(sampleEvent)
                .seat(physSeat3)
                .price(new BigDecimal("2500.00"))
                .status(SeatStatus.AVAILABLE)
                .build());
    }

    @AfterEach
    void tearDown() {
        org.mockito.Mockito.reset(redisCoordinator);
        try {
            if (sampleEvent != null) {
                redisCoordinator.releaseMultiSeatHold(sampleEvent.getId(),
                        Arrays.asList(seat1.getId(), seat2.getId(), seat3.getId()),
                        redisCoordinator.getHoldToken(sampleEvent.getId(), seat1.getId()));
            }
        } catch (Exception ignored) {
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
    @DisplayName("1. Successful checkout: PENDING -> BOOKED CAS -> Mock Payment -> CONFIRMED -> Outbox -> CAD")
    void shouldCompleteSuccessfulCheckout() {
        // Customer 1 holds seats 1 and 2
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .holdToken(holdResp.getHoldToken())
                .idempotencyKey("checkout-key-001")
                .paymentMethod("MOCK_CARD_VALID")
                .build();

        BookingResponse response = bookingService.createBooking(checkoutReq, customer1.getId());

        assertNotNull(response);
        assertNotNull(response.getBookingId());
        assertTrue(response.getBookingReference().startsWith("SS-2026-"));
        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(new BigDecimal("5000.00"), response.getTotalAmount());
        assertEquals(2, response.getItems().size());

        // Verify MySQL seats updated to BOOKED
        EventSeat s1 = eventSeatRepository.findById(seat1.getId()).orElseThrow();
        EventSeat s2 = eventSeatRepository.findById(seat2.getId()).orElseThrow();
        assertEquals(SeatStatus.BOOKED, s1.getStatus());
        assertEquals(SeatStatus.BOOKED, s2.getStatus());
        assertEquals(response.getBookingId(), s1.getCurrentBooking().getId());
        assertNull(s1.getHoldToken());
        assertNull(s1.getHeldByUser());

        // Verify booking_items
        List<BookingItem> items = bookingItemRepository.findByBookingId(response.getBookingId());
        assertEquals(2, items.size());

        // Verify outbox_events inserted
        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        Optional<OutboxEvent> bookingOutboxOpt = outboxList.stream()
                .filter(o -> "BOOKING_CONFIRMED".equals(o.getEventType()))
                .findFirst();
        assertTrue(bookingOutboxOpt.isPresent(), "Expected BOOKING_CONFIRMED outbox event");
        OutboxEvent outbox = bookingOutboxOpt.get();
        assertEquals("BOOKING", outbox.getAggregateType());
        assertEquals(response.getBookingId().toString(), outbox.getAggregateId());
        assertEquals("BOOKING_CONFIRMED", outbox.getEventType());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());

        // Verify Redis keys removed via CAD
        assertFalse(redisCoordinator.isSeatHeld(sampleEvent.getId(), seat1.getId()));
        assertFalse(redisCoordinator.isSeatHeld(sampleEvent.getId(), seat2.getId()));
    }

    @Test
    @DisplayName("2. Invalid hold token: Must reject checkout and leave seats HELD")
    void shouldRejectInvalidHoldToken() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .build();
        seatHoldService.holdSeats(holdReq, customer1.getId());

        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .holdToken("invalid-forged-token-xyz")
                .idempotencyKey("checkout-bad-token")
                .build();

        assertThrows(BadRequestException.class, () ->
                bookingService.createBooking(checkoutReq, customer1.getId()));

        // Seat must still remain HELD in MySQL
        EventSeat s1 = eventSeatRepository.findById(seat1.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, s1.getStatus());
        assertEquals(0, bookingRepository.count());
    }

    @Test
    @DisplayName("3. Expired hold: Must reject checkout with 409 Conflict")
    void shouldRejectExpiredHold() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        // Manually expire the hold in MySQL
        seat1 = eventSeatRepository.findById(seat1.getId()).orElseThrow();
        seat1.setHeldUntil(Instant.now().minus(5, ChronoUnit.MINUTES));
        eventSeatRepository.save(seat1);

        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .holdToken(holdResp.getHoldToken())
                .idempotencyKey("checkout-expired")
                .build();

        assertThrows(SeatHoldConflictException.class, () ->
                bookingService.createBooking(checkoutReq, customer1.getId()));

        assertEquals(0, bookingRepository.count());
    }

    @Test
    @DisplayName("4. Another user's hold: Must reject with 403 Forbidden")
    void shouldRejectCheckoutOfAnotherUsersHold() {
        // Customer 1 holds seat 1
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        // Customer 2 attempts to checkout Customer 1's hold
        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .holdToken(holdResp.getHoldToken())
                .idempotencyKey("checkout-cust2-steals-cust1")
                .build();

        assertThrows(ForbiddenException.class, () ->
                bookingService.createBooking(checkoutReq, customer2.getId()));

        assertEquals(0, bookingRepository.count());
        EventSeat s1 = eventSeatRepository.findById(seat1.getId()).orElseThrow();
        assertEquals(customer1.getId(), s1.getHeldByUser().getId());
    }

    @Test
    @DisplayName("5. Duplicate seat IDs: Must reject with 400 Bad Request")
    void shouldRejectDuplicateSeatIds() {
        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat1.getId()))
                .holdToken("dummy-token")
                .idempotencyKey("checkout-dupe-seats")
                .build();

        assertThrows(BadRequestException.class, () ->
                bookingService.createBooking(checkoutReq, customer1.getId()));
    }

    @Test
    @DisplayName("6. Insufficient seat update count: CAS mismatch rolls back entire booking")
    void shouldRollbackWhenCasUpdateFails() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        // Sabotage seat 2 before checkout (e.g. status changed to AVAILABLE)
        seat2 = eventSeatRepository.findById(seat2.getId()).orElseThrow();
        seat2.setStatus(SeatStatus.AVAILABLE);
        seat2.setHoldToken(null);
        eventSeatRepository.save(seat2);

        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Arrays.asList(seat1.getId(), seat2.getId()))
                .holdToken(holdResp.getHoldToken())
                .idempotencyKey("checkout-cas-mismatch")
                .build();

        assertThrows(SeatHoldConflictException.class, () ->
                bookingService.createBooking(checkoutReq, customer1.getId()));

        // Zero bookings created (complete rollback)
        assertEquals(0, bookingRepository.count());
        assertEquals(0, bookingItemRepository.count());
    }

    @Test
    @DisplayName("7. Idempotent retry: Replaying identical (userId, idempotencyKey) returns same booking")
    void shouldReturnSameBookingOnIdempotentRetry() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .holdToken(holdResp.getHoldToken())
                .idempotencyKey("idempotent-key-42")
                .build();

        BookingResponse resp1 = bookingService.createBooking(checkoutReq, customer1.getId());
        BookingResponse resp2 = bookingService.createBooking(checkoutReq, customer1.getId());

        assertEquals(resp1.getBookingId(), resp2.getBookingId());
        assertEquals(resp1.getBookingReference(), resp2.getBookingReference());
        assertEquals(1, bookingRepository.count(), "Only 1 booking row must exist");
    }

    @Test
    @DisplayName("8. Concurrent checkout: Concurrent race on same idempotencyKey resolves to 1 booking")
    void shouldHandleConcurrentIdempotentCheckouts() throws Exception {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<BookingResponse> responses = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CreateBookingRequest req = CreateBookingRequest.builder()
                            .eventId(sampleEvent.getId())
                            .eventSeatIds(Collections.singletonList(seat1.getId()))
                            .holdToken(holdResp.getHoldToken())
                            .idempotencyKey("concurrent-key-999")
                            .build();
                    BookingResponse resp = bookingService.createBooking(req, customer1.getId());
                    responses.add(resp);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(), "All threads should successfully resolve");
        assertEquals(1, bookingRepository.count(), "Only one physical booking row in database");
        Long firstBookingId = responses.get(0).getBookingId();
        for (BookingResponse r : responses) {
            assertEquals(firstBookingId, r.getBookingId());
        }
    }

    @Test
    @DisplayName("9. Mock payment failure: Rolls back complete transaction, seats remain HELD")
    void shouldRollbackOnPaymentFailure() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());
        outboxEventRepository.deleteAll(); // Clear prior hold event to verify checkout rollback

        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .holdToken(holdResp.getHoldToken())
                .idempotencyKey("checkout-payment-fail")
                .paymentMethod("MOCK_CARD_FAIL") // Triggers simulated decline
                .build();

        assertThrows(PaymentFailedException.class, () ->
                bookingService.createBooking(checkoutReq, customer1.getId()));

        // Verification: No booking committed, seat was rolled back and remains HELD
        assertEquals(0, bookingRepository.count());
        assertEquals(0, bookingItemRepository.count());
        assertEquals(0, outboxEventRepository.count(), "Failed booking transaction must commit zero outbox events");
        EventSeat s1 = eventSeatRepository.findById(seat1.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, s1.getStatus());
    }

    @Test
    @DisplayName("10. Redis cleanup failure after commit: Must NOT undo committed booking")
    void shouldPreserveCommittedBookingIfRedisCleanupFails() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        // Simulate Redis throwing a runtime exception during post-commit CAD
        doThrow(new RuntimeException("Simulated Redis Connection Glitch"))
                .when(redisCoordinator)
                .releaseMultiSeatHold(anyLong(), any(), anyString());

        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .holdToken(holdResp.getHoldToken())
                .idempotencyKey("checkout-redis-fail")
                .build();

        // The checkout must still succeed without throwing exception
        BookingResponse response = bookingService.createBooking(checkoutReq, customer1.getId());

        assertNotNull(response);
        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(1, bookingRepository.count(), "Committed booking must remain intact");
        EventSeat s1 = eventSeatRepository.findById(seat1.getId()).orElseThrow();
        assertEquals(SeatStatus.BOOKED, s1.getStatus(), "Seat must remain BOOKED in MySQL");
    }

    @Test
    @DisplayName("11. Booking history and detail authorization")
    void testBookingHistoryAndDetailAuthorization() throws Exception {
        // Customer 1 creates a booking
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        CreateBookingRequest checkoutReq = CreateBookingRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seat1.getId()))
                .holdToken(holdResp.getHoldToken())
                .idempotencyKey("auth-history-key-1")
                .build();
        BookingResponse booking1 = bookingService.createBooking(checkoutReq, customer1.getId());

        // Customer 1 queries GET /api/bookings/my -> Sees 1 booking
        mockMvc.perform(get("/api/bookings/my")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].bookingId", is(booking1.getBookingId().intValue())));

        // Customer 2 queries GET /api/bookings/my -> Sees 0 bookings (owner isolation)
        mockMvc.perform(get("/api/bookings/my")
                        .header("Authorization", "Bearer " + customer2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // Customer 1 queries GET /api/bookings/{id} -> 200 OK
        mockMvc.perform(get("/api/bookings/" + booking1.getBookingId())
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingId", is(booking1.getBookingId().intValue())));

        // Customer 2 queries GET /api/bookings/{id} -> 403 Forbidden (not owner)
        mockMvc.perform(get("/api/bookings/" + booking1.getBookingId())
                        .header("Authorization", "Bearer " + customer2Token))
                .andExpect(status().isForbidden());

        // Admin queries GET /api/bookings/{id} -> 200 OK (admin privilege)
        mockMvc.perform(get("/api/bookings/" + booking1.getBookingId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingId", is(booking1.getBookingId().intValue())));

        // Organizer queries GET /api/bookings/my -> 403 Forbidden (rejects organizer)
        mockMvc.perform(get("/api/bookings/my")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isForbidden());
    }
}

