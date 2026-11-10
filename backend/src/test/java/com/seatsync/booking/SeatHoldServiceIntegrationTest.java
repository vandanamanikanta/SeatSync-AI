package com.seatsync.booking;

import com.seatsync.common.exception.BadRequestException;
import com.seatsync.common.exception.ForbiddenException;
import com.seatsync.common.exception.ResourceNotFoundException;
import com.seatsync.common.exception.SeatHoldConflictException;
import com.seatsync.modules.booking.dto.request.SeatHoldRequest;
import com.seatsync.modules.booking.dto.request.SeatReleaseRequest;
import com.seatsync.modules.booking.dto.response.SeatHoldResponse;
import com.seatsync.modules.booking.dto.response.SeatReleaseResponse;
import com.seatsync.modules.booking.entity.EventSeat;
import com.seatsync.modules.booking.entity.SeatStatus;
import com.seatsync.modules.booking.redis.RedisSeatHoldCoordinator;
import com.seatsync.modules.booking.repository.EventSeatRepository;
import com.seatsync.modules.booking.service.SeatHoldService;
import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventCategory;
import com.seatsync.modules.event.entity.EventStatus;
import com.seatsync.modules.event.entity.Seat;
import com.seatsync.modules.event.entity.Venue;
import com.seatsync.modules.event.entity.VenueSection;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SeatHoldServiceIntegrationTest {

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private RedisSeatHoldCoordinator redisCoordinator;

    @Autowired
    private EventSeatRepository eventSeatRepository;

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
    private Event testEvent;
    private EventSeat eventSeat1;
    private EventSeat eventSeat2;
    private EventSeat eventSeat3;

    @BeforeEach
    void setUp() {
        eventSeatRepository.deleteAll();
        seatRepository.deleteAll();
        venueSectionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        // Roles
        Role customerRole = roleRepository.save(new Role("ROLE_CUSTOMER", "Customer"));
        Role adminRole = roleRepository.save(new Role("ROLE_ADMIN", "Administrator"));

        // Users
        customer1 = userRepository.save(User.builder()
                .email("cust1@example.com")
                .passwordHash("hash")
                .fullName("Customer One")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build());

        customer2 = userRepository.save(User.builder()
                .email("cust2@example.com")
                .passwordHash("hash")
                .fullName("Customer Two")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build());

        adminUser = userRepository.save(User.builder()
                .email("admin@example.com")
                .passwordHash("hash")
                .fullName("Admin User")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(adminRole))
                .build());

        // Venue & Sections
        Venue venue = venueRepository.save(Venue.builder()
                .name("Grand Test Arena")
                .slug("grand-test-arena")
                .address("100 Test Blvd")
                .city("Hyderabad")
                .state("Telangana")
                .postalCode("500081")
                .capacity(1000)
                .build());

        VenueSection section = venueSectionRepository.save(VenueSection.builder()
                .venue(venue)
                .name("VIP Section")
                .sectionCode("VIP")
                .tierLevel(1)
                .capacity(50)
                .displayOrder(1)
                .build());

        // Physical Seats
        Seat seat1 = seatRepository.save(Seat.builder()
                .venueSection(section)
                .rowLabel("A")
                .seatNumber(1)
                .gridX(1)
                .gridY(1)
                .build());

        Seat seat2 = seatRepository.save(Seat.builder()
                .venueSection(section)
                .rowLabel("A")
                .seatNumber(2)
                .gridX(2)
                .gridY(1)
                .build());

        Seat seat3 = seatRepository.save(Seat.builder()
                .venueSection(section)
                .rowLabel("A")
                .seatNumber(3)
                .gridX(3)
                .gridY(1)
                .build());

        // Event Category
        EventCategory cat = categoryRepository.save(EventCategory.builder()
                .name("Concerts")
                .slug("concerts")
                .build());

        // Event
        testEvent = eventRepository.save(Event.builder()
                .title("Symphony 2026")
                .slug("symphony-2026")
                .shortDescription("Live musical concert")
                .category(cat)
                .venue(venue)
                .organizer(customer1)
                .startTime(Instant.now().plus(7, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(7, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .basePrice(new BigDecimal("1500.00"))
                .status(EventStatus.PUBLISHED)
                .build());

        // Event Seats
        eventSeat1 = eventSeatRepository.save(EventSeat.builder()
                .event(testEvent)
                .seat(seat1)
                .price(new BigDecimal("1500.00"))
                .status(SeatStatus.AVAILABLE)
                .build());

        eventSeat2 = eventSeatRepository.save(EventSeat.builder()
                .event(testEvent)
                .seat(seat2)
                .price(new BigDecimal("1500.00"))
                .status(SeatStatus.AVAILABLE)
                .build());

        eventSeat3 = eventSeatRepository.save(EventSeat.builder()
                .event(testEvent)
                .seat(seat3)
                .price(new BigDecimal("1500.00"))
                .status(SeatStatus.AVAILABLE)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (testEvent != null) {
            redisCoordinator.releaseMultiSeatHold(testEvent.getId(),
                    Arrays.asList(eventSeat1.getId(), eventSeat2.getId(), eventSeat3.getId()),
                    redisCoordinator.getHoldToken(testEvent.getId(), eventSeat1.getId()));
            redisCoordinator.releaseMultiSeatHold(testEvent.getId(),
                    Arrays.asList(eventSeat1.getId(), eventSeat2.getId(), eventSeat3.getId()),
                    redisCoordinator.getHoldToken(testEvent.getId(), eventSeat2.getId()));
        }
        eventSeatRepository.deleteAll();
        seatRepository.deleteAll();
        venueSectionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully acquire multi-seat hold in Redis and MySQL")
    void shouldHoldSeatsSuccessfully() {
        SeatHoldRequest request = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Arrays.asList(eventSeat1.getId(), eventSeat2.getId()))
                .build();

        SeatHoldResponse response = seatHoldService.holdSeats(request, customer1.getId());

        assertNotNull(response);
        assertNotNull(response.getHoldToken());
        assertEquals(2, response.getHeldSeatIds().size());
        assertEquals(300, response.getTtlSecondsRemaining());

        // Verify MySQL updated
        EventSeat updated1 = eventSeatRepository.findById(eventSeat1.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, updated1.getStatus());
        assertEquals(customer1.getId(), updated1.getHeldByUser().getId());
        assertEquals(response.getHoldToken(), updated1.getHoldToken());
        assertNotNull(updated1.getHeldUntil());

        // Verify Redis updated
        assertEquals(response.getHoldToken(), redisCoordinator.getHoldToken(testEvent.getId(), eventSeat1.getId()));
        assertEquals(response.getHoldToken(), redisCoordinator.getHoldToken(testEvent.getId(), eventSeat2.getId()));
    }

    @Test
    @DisplayName("Should reject hold when requested seat is already held (Conflict 409)")
    void shouldRejectHoldWhenSeatAlreadyHeld() {
        // Customer 1 holds seat 1
        SeatHoldRequest req1 = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Collections.singletonList(eventSeat1.getId()))
                .build();
        SeatHoldResponse resp1 = seatHoldService.holdSeats(req1, customer1.getId());

        // Customer 2 attempts to hold seat 1 and seat 2
        SeatHoldRequest req2 = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Arrays.asList(eventSeat1.getId(), eventSeat2.getId()))
                .build();

        SeatHoldConflictException ex = assertThrows(SeatHoldConflictException.class, () ->
                seatHoldService.holdSeats(req2, customer2.getId()));

        assertNotNull(ex.getMessage());
        // Verify customer 1's Redis key was NOT wiped or touched
        assertEquals(resp1.getHoldToken(), redisCoordinator.getHoldToken(testEvent.getId(), eventSeat1.getId()));
        // Verify seat 2 was not held
        assertFalse(redisCoordinator.isSeatHeld(testEvent.getId(), eventSeat2.getId()));
    }

    @Test
    @DisplayName("Should release held seats and remove matching Redis keys via Compare-and-Delete")
    void shouldReleaseSeatsSuccessfully() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Arrays.asList(eventSeat1.getId(), eventSeat2.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        SeatReleaseRequest releaseReq = SeatReleaseRequest.builder()
                .eventId(testEvent.getId())
                .holdToken(holdResp.getHoldToken())
                .eventSeatIds(Arrays.asList(eventSeat1.getId(), eventSeat2.getId()))
                .build();

        SeatReleaseResponse releaseResp = seatHoldService.releaseSeats(releaseReq, customer1.getId(), false);

        assertEquals(2, releaseResp.getReleasedCount());

        // Verify MySQL reset to AVAILABLE
        EventSeat s1 = eventSeatRepository.findById(eventSeat1.getId()).orElseThrow();
        assertEquals(SeatStatus.AVAILABLE, s1.getStatus());
        assertNull(s1.getHeldByUser());
        assertNull(s1.getHoldToken());
        assertNull(s1.getHeldUntil());

        // Verify Redis cleared
        assertFalse(redisCoordinator.isSeatHeld(testEvent.getId(), eventSeat1.getId()));
        assertFalse(redisCoordinator.isSeatHeld(testEvent.getId(), eventSeat2.getId()));
    }

    @Test
    @DisplayName("Should prevent non-owner from releasing another user's hold (Forbidden 403)")
    void shouldRejectReleaseByNonOwner() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Collections.singletonList(eventSeat1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        SeatReleaseRequest releaseReq = SeatReleaseRequest.builder()
                .eventId(testEvent.getId())
                .holdToken(holdResp.getHoldToken())
                .build();

        // Customer 2 attempts to release Customer 1's hold
        assertThrows(ForbiddenException.class, () ->
                seatHoldService.releaseSeats(releaseReq, customer2.getId(), false));

        // Seat 1 should still be held
        EventSeat s1 = eventSeatRepository.findById(eventSeat1.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, s1.getStatus());
    }

    @Test
    @DisplayName("Admin should be permitted to release any hold")
    void shouldAllowAdminToReleaseAnyHold() {
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Collections.singletonList(eventSeat1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customer1.getId());

        SeatReleaseRequest releaseReq = SeatReleaseRequest.builder()
                .eventId(testEvent.getId())
                .holdToken(holdResp.getHoldToken())
                .build();

        // Admin releases Customer 1's hold
        SeatReleaseResponse releaseResp = seatHoldService.releaseSeats(releaseReq, adminUser.getId(), true);
        assertEquals(1, releaseResp.getReleasedCount());
    }

    @Test
    @DisplayName("Safe compensation: When MySQL fails, Redis keys must be immediately released via CAD")
    void shouldCompensateRedisWhenMySqlUpdateFails() {
        // Create a request with an invalid/non-existent seat ID to trigger MySQL failure
        SeatHoldRequest request = SeatHoldRequest.builder()
                .eventId(testEvent.getId())
                .eventSeatIds(Arrays.asList(eventSeat1.getId(), 99999L))
                .build();

        assertThrows(BadRequestException.class, () ->
                seatHoldService.holdSeats(request, customer1.getId()));

        // Compensation verification: eventSeat1 must NOT remain locked in Redis!
        assertFalse(redisCoordinator.isSeatHeld(testEvent.getId(), eventSeat1.getId()),
                "Redis key for seat 1 must be compensated and evicted immediately upon MySQL failure");
    }

    @Test
    @DisplayName("Expired hold reconciliation: Sweeper safely resets expired holds under row lock")
    void shouldReconcileExpiredHoldsSafely() {
        // Manually create an expired hold in MySQL
        String expiredToken = UUID.randomUUID().toString();
        eventSeat1.setStatus(SeatStatus.HELD);
        eventSeat1.setHeldByUser(customer1);
        eventSeat1.setHoldToken(expiredToken);
        eventSeat1.setHeldUntil(Instant.now().minus(10, ChronoUnit.MINUTES));
        eventSeatRepository.save(eventSeat1);

        // Put corresponding key in Redis
        redisCoordinator.acquireMultiSeatHold(testEvent.getId(), Collections.singletonList(eventSeat1.getId()), expiredToken, 300);

        // Run reconciliation sweeper
        int cleaned = seatHoldService.reconcileExpiredHolds();
        assertTrue(cleaned >= 1);

        // Verify MySQL reset to AVAILABLE
        EventSeat reconciled = eventSeatRepository.findById(eventSeat1.getId()).orElseThrow();
        assertEquals(SeatStatus.AVAILABLE, reconciled.getStatus());
        assertNull(reconciled.getHoldToken());

        // Verify Redis CAD released the expired key
        assertFalse(redisCoordinator.isSeatHeld(testEvent.getId(), eventSeat1.getId()));
    }

    @Test
    @DisplayName("High-concurrency test: Exactly ONE thread succeeds when 10 threads compete for same seat")
    void shouldHandleHighConcurrencyCorrectly() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final Long uid = (i % 2 == 0) ? customer1.getId() : customer2.getId();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    SeatHoldRequest req = SeatHoldRequest.builder()
                            .eventId(testEvent.getId())
                            .eventSeatIds(Collections.singletonList(eventSeat3.getId()))
                            .build();
                    seatHoldService.holdSeats(req, uid);
                    successCount.incrementAndGet();
                } catch (SeatHoldConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // other exceptions
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Start all threads at the exact same millisecond
        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one thread must successfully acquire the seat hold");
        assertEquals(9, conflictCount.get(), "All 9 competing threads must receive conflict rejection");

        EventSeat finalSeat = eventSeatRepository.findById(eventSeat3.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, finalSeat.getStatus());
    }
}

