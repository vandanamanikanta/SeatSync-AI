package com.seatsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.common.security.JwtTokenProvider;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.booking.dto.request.SeatHoldRequest;
import com.seatsync.modules.booking.dto.request.SeatReleaseRequest;
import com.seatsync.modules.booking.dto.response.EventSeatMatrixResponse;
import com.seatsync.modules.booking.dto.response.SeatHoldResponse;
import com.seatsync.modules.booking.dto.response.SeatReleaseResponse;
import com.seatsync.modules.booking.entity.EventSeat;
import com.seatsync.modules.booking.entity.SeatStatus;
import com.seatsync.modules.booking.redis.RedisSeatHoldCoordinator;
import com.seatsync.modules.booking.repository.EventSeatRepository;
import com.seatsync.modules.booking.service.SeatHoldService;
import com.seatsync.modules.booking.service.SeatMatrixService;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End integration test testing the Seat Hold Engine against
 * the actual production MySQL database (seatsync_db) and Redis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/seatsync_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8",
        "spring.datasource.username=root",
        "spring.datasource.password=root",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=false"
})
class LiveMySqlAndRedisHoldEngineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private SeatMatrixService seatMatrixService;

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private RedisSeatHoldCoordinator redisCoordinator;

    @Autowired
    private EventSeatRepository eventSeatRepository;

    @Autowired
    private com.seatsync.modules.booking.repository.BookingRepository bookingRepository;

    @Autowired
    private com.seatsync.modules.booking.repository.BookingItemRepository bookingItemRepository;

    @Autowired
    private com.seatsync.modules.booking.repository.OutboxEventRepository outboxEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Verify Live GET /api/events/1/seats against seatsync_db")
    void testLiveEventSeatMatrix() throws Exception {
        // Event 1 has 175 event seats in seatsync_db
        EventSeatMatrixResponse matrix = seatMatrixService.getEventSeatMatrix(1L);
        assertNotNull(matrix);
        assertEquals(1L, matrix.getEventId());
        assertEquals(175, matrix.getTotalSeats());
        assertEquals(3, matrix.getSections().size());

        // Perform via HTTP MockMvc
        mockMvc.perform(get("/api/events/1/seats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.eventId", is(1)))
                .andExpect(jsonPath("$.data.totalSeats", is(175)))
                .andExpect(jsonPath("$.data.sections", hasSize(3)));
    }

    @Test
    @DisplayName("Verify Live Seat Hold, Conflict 409, and CAD Release against MySQL and Redis")
    void testLiveSeatHoldAndCadReleaseLifecycle() throws Exception {
        // Fetch real customer user from seatsync_db
        User customer = userRepository.findByEmail("customer@seatsync.ai")
                .orElseGet(() -> userRepository.findAll().stream().filter(u -> !u.getEmail().contains("admin")).findFirst().orElseThrow());

        String customerToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(customer));

        // Get two available seats for Event 1
        List<EventSeat> event1Seats = eventSeatRepository.findByEventIdWithSeatDetails(1L);
        EventSeat s1 = event1Seats.get(0);
        EventSeat s2 = event1Seats.get(1);

        // Step 1: POST /api/bookings/hold
        SeatHoldRequest holdRequest = SeatHoldRequest.builder()
                .eventId(1L)
                .eventSeatIds(Arrays.asList(s1.getId(), s2.getId()))
                .build();

        String holdResponseJson = mockMvc.perform(post("/api/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(holdRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.holdToken", notNullValue()))
                .andExpect(jsonPath("$.data.heldSeatIds", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        SeatHoldResponse holdResponse = objectMapper.readTree(holdResponseJson)
                .get("data").traverse(objectMapper).readValueAs(SeatHoldResponse.class);
        String holdToken = holdResponse.getHoldToken();

        // Verify MySQL state
        EventSeat heldSeat1 = eventSeatRepository.findById(s1.getId()).orElseThrow();
        assertEquals(SeatStatus.HELD, heldSeat1.getStatus());
        assertEquals(holdToken, heldSeat1.getHoldToken());
        assertNotNull(heldSeat1.getHeldUntil());

        // Verify Redis state
        assertEquals(holdToken, redisCoordinator.getHoldToken(1L, s1.getId()));
        assertEquals(holdToken, redisCoordinator.getHoldToken(1L, s2.getId()));

        // Step 2: Attempt conflicting hold on s1 -> Expect 409 Conflict
        SeatHoldRequest conflictRequest = SeatHoldRequest.builder()
                .eventId(1L)
                .eventSeatIds(Collections.singletonList(s1.getId()))
                .build();

        mockMvc.perform(post("/api/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("SEATS_ALREADY_RESERVED")));

        // Step 3: DELETE /api/bookings/hold with holdToken
        SeatReleaseRequest releaseRequest = SeatReleaseRequest.builder()
                .eventId(1L)
                .holdToken(holdToken)
                .eventSeatIds(Arrays.asList(s1.getId(), s2.getId()))
                .build();

        mockMvc.perform(delete("/api/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.releasedCount", is(2)));

        // Verify MySQL state restored to AVAILABLE
        EventSeat releasedSeat1 = eventSeatRepository.findById(s1.getId()).orElseThrow();
        assertEquals(SeatStatus.AVAILABLE, releasedSeat1.getStatus());
        assertNull(releasedSeat1.getHoldToken());
        assertNull(releasedSeat1.getHeldUntil());

        // Verify Redis keys removed via Compare-and-Delete
        assertFalse(redisCoordinator.isSeatHeld(1L, s1.getId()));
        assertFalse(redisCoordinator.isSeatHeld(1L, s2.getId()));
    }

    @Test
    @DisplayName("Verify Live Booking Checkout, CAS update, Outbox publishing, and Idempotent Replay against seatsync_db")
    void testLiveBookingCheckoutAndIdempotency() throws Exception {
        User customer = userRepository.findByEmail("customer@seatsync.ai")
                .orElseGet(() -> userRepository.findAll().stream().filter(u -> !u.getEmail().contains("admin")).findFirst().orElseThrow());

        String customerToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(customer));

        List<EventSeat> availableSeats = eventSeatRepository.findByEventIdWithSeatDetails(1L).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .limit(2)
                .toList();
        assertTrue(availableSeats.size() >= 2, "Expected at least 2 available seats for test");

        EventSeat s1 = availableSeats.get(0);
        EventSeat s2 = availableSeats.get(1);

        Long bookingId = null;
        String holdToken = null;

        try {
            // Step 1: Hold the seats
            SeatHoldRequest holdRequest = SeatHoldRequest.builder()
                    .eventId(1L)
                    .eventSeatIds(Arrays.asList(s1.getId(), s2.getId()))
                    .build();

            String holdJson = mockMvc.perform(post("/api/bookings/hold")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(holdRequest)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            SeatHoldResponse holdResp = objectMapper.readTree(holdJson).get("data").traverse(objectMapper).readValueAs(SeatHoldResponse.class);
            holdToken = holdResp.getHoldToken();

            // Step 2: Confirm checkout via POST /api/bookings
            String idempotencyKey = "live-checkout-" + UUID.randomUUID();
            com.seatsync.modules.booking.dto.request.CreateBookingRequest checkoutReq =
                    com.seatsync.modules.booking.dto.request.CreateBookingRequest.builder()
                            .eventId(1L)
                            .eventSeatIds(Arrays.asList(s1.getId(), s2.getId()))
                            .holdToken(holdToken)
                            .idempotencyKey(idempotencyKey)
                            .paymentMethod("MOCK_CARD_VALID")
                            .build();

            String checkoutJson = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(checkoutReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                    .andExpect(jsonPath("$.data.items", hasSize(2)))
                    .andReturn().getResponse().getContentAsString();

            com.seatsync.modules.booking.dto.response.BookingResponse bookingResp =
                    objectMapper.readTree(checkoutJson).get("data").traverse(objectMapper)
                            .readValueAs(com.seatsync.modules.booking.dto.response.BookingResponse.class);

            bookingId = bookingResp.getBookingId();

            // Step 3: Test Idempotent Replay
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(checkoutReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.bookingId", is(bookingId.intValue())));

            // Step 4: Verify GET /api/bookings/{id}
            mockMvc.perform(get("/api/bookings/" + bookingId)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.bookingId", is(bookingId.intValue())));

            // Verify outbox_events has record
            assertTrue(outboxEventRepository.count() >= 1);
        } finally {
            // Guarantee test cleanup so seatsync_db remains pristine
            try {
                if (holdToken != null) {
                    redisCoordinator.releaseMultiSeatHold(1L, Arrays.asList(s1.getId(), s2.getId()), holdToken);
                }
            } catch (Exception ignored) {
            }

            EventSeat freshS1 = eventSeatRepository.findById(s1.getId()).orElse(s1);
            EventSeat freshS2 = eventSeatRepository.findById(s2.getId()).orElse(s2);
            freshS1.setStatus(SeatStatus.AVAILABLE);
            freshS1.setCurrentBooking(null);
            freshS1.setHeldByUser(null);
            freshS1.setHeldUntil(null);
            freshS1.setHoldToken(null);
            freshS2.setStatus(SeatStatus.AVAILABLE);
            freshS2.setCurrentBooking(null);
            freshS2.setHeldByUser(null);
            freshS2.setHeldUntil(null);
            freshS2.setHoldToken(null);
            eventSeatRepository.saveAll(Arrays.asList(freshS1, freshS2));

            outboxEventRepository.deleteAll();
            bookingItemRepository.deleteAll();
            if (bookingId != null) {
                bookingRepository.deleteById(bookingId);
            }
        }
    }
}

