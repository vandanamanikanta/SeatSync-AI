package com.seatsync.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.common.security.JwtTokenProvider;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.booking.dto.request.SeatHoldRequest;
import com.seatsync.modules.booking.dto.request.SeatReleaseRequest;
import com.seatsync.modules.booking.dto.response.SeatHoldResponse;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeatMatrixAndHoldControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private RedisSeatHoldCoordinator redisCoordinator;

    private User customerUser;
    private User adminUser;
    private User organizerUser;
    private String customerToken;
    private String adminToken;
    private String organizerToken;
    private Event sampleEvent;
    private EventSeat seatInstance1;
    private EventSeat seatInstance2;

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

        Role customerRole = roleRepository.save(new Role("ROLE_CUSTOMER", "Customer"));
        Role adminRole = roleRepository.save(new Role("ROLE_ADMIN", "Admin"));
        Role organizerRole = roleRepository.save(new Role("ROLE_ORGANIZER", "Organizer"));

        customerUser = userRepository.save(User.builder()
                .email("cust@test.com")
                .passwordHash("hash")
                .fullName("Customer Test")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build());

        adminUser = userRepository.save(User.builder()
                .email("admin@test.com")
                .passwordHash("hash")
                .fullName("Admin Test")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(adminRole))
                .build());

        organizerUser = userRepository.save(User.builder()
                .email("org@test.com")
                .passwordHash("hash")
                .fullName("Organizer Test")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(organizerRole))
                .build());

        customerToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(customerUser));
        adminToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(adminUser));
        organizerToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(organizerUser));

        Venue venue = venueRepository.save(Venue.builder()
                .name("Metro Arena")
                .slug("metro-arena")
                .address("123 Main St")
                .city("Mumbai")
                .state("Maharashtra")
                .postalCode("400001")
                .capacity(500)
                .build());

        VenueSection section = venueSectionRepository.save(VenueSection.builder()
                .venue(venue)
                .name("Platinum Circle")
                .sectionCode("PLATINUM")
                .tierLevel(1)
                .capacity(100)
                .displayOrder(1)
                .build());

        Seat physicalSeat1 = seatRepository.save(Seat.builder()
                .venueSection(section)
                .rowLabel("A")
                .seatNumber(1)
                .gridX(10)
                .gridY(20)
                .build());

        Seat physicalSeat2 = seatRepository.save(Seat.builder()
                .venueSection(section)
                .rowLabel("A")
                .seatNumber(2)
                .gridX(11)
                .gridY(20)
                .build());

        EventCategory category = categoryRepository.save(EventCategory.builder()
                .name("Rock Concert")
                .slug("rock-concert")
                .build());

        sampleEvent = eventRepository.save(Event.builder()
                .title("Rock Mania 2026")
                .slug("rock-mania-2026")
                .shortDescription("Live Rock Show")
                .category(category)
                .venue(venue)
                .organizer(organizerUser)
                .startTime(Instant.now().plus(5, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(5, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS))
                .basePrice(new BigDecimal("2000.00"))
                .status(EventStatus.PUBLISHED)
                .build());

        seatInstance1 = eventSeatRepository.save(EventSeat.builder()
                .event(sampleEvent)
                .seat(physicalSeat1)
                .price(new BigDecimal("2000.00"))
                .status(SeatStatus.AVAILABLE)
                .build());

        seatInstance2 = eventSeatRepository.save(EventSeat.builder()
                .event(sampleEvent)
                .seat(physicalSeat2)
                .price(new BigDecimal("2000.00"))
                .status(SeatStatus.AVAILABLE)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (sampleEvent != null) {
            redisCoordinator.releaseMultiSeatHold(sampleEvent.getId(),
                    Arrays.asList(seatInstance1.getId(), seatInstance2.getId()),
                    redisCoordinator.getHoldToken(sampleEvent.getId(), seatInstance1.getId()));
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
    @DisplayName("GET /api/events/{eventId}/seats - Should be publicly accessible and return hierarchical matrix")
    void shouldReturnEventSeatMatrixPublicly() throws Exception {
        mockMvc.perform(get("/api/events/" + sampleEvent.getId() + "/seats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.eventId", is(sampleEvent.getId().intValue())))
                .andExpect(jsonPath("$.data.totalSeats", is(2)))
                .andExpect(jsonPath("$.data.availableSeats", is(2)))
                .andExpect(jsonPath("$.data.sections", hasSize(1)))
                .andExpect(jsonPath("$.data.sections[0].sectionName", is("Platinum Circle")))
                .andExpect(jsonPath("$.data.sections[0].seats", hasSize(2)));
    }

    @Test
    @DisplayName("POST /api/bookings/hold - CUSTOMER should successfully hold seats (200 OK)")
    void shouldAllowCustomerToHoldSeats() throws Exception {
        SeatHoldRequest request = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Arrays.asList(seatInstance1.getId(), seatInstance2.getId()))
                .build();

        mockMvc.perform(post("/api/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.holdToken", notNullValue()))
                .andExpect(jsonPath("$.data.ttlSecondsRemaining", is(300)))
                .andExpect(jsonPath("$.data.heldSeatIds", hasSize(2)));
    }

    @Test
    @DisplayName("POST /api/bookings/hold - ADMIN should successfully hold seats (200 OK)")
    void shouldAllowAdminToHoldSeats() throws Exception {
        SeatHoldRequest request = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seatInstance1.getId()))
                .build();

        mockMvc.perform(post("/api/bookings/hold")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("POST /api/bookings/hold - ORGANIZER should be REJECTED (403 Forbidden)")
    void shouldRejectOrganizerFromHoldingSeats() throws Exception {
        SeatHoldRequest request = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seatInstance1.getId()))
                .build();

        mockMvc.perform(post("/api/bookings/hold")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/bookings/hold - Unauthenticated request should be REJECTED (401 Unauthorized)")
    void shouldRejectUnauthenticatedHoldRequest() throws Exception {
        SeatHoldRequest request = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seatInstance1.getId()))
                .build();

        mockMvc.perform(post("/api/bookings/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/bookings/hold - Validation error when eventId missing or seats empty (400 Bad Request)")
    void shouldRejectInvalidHoldPayload() throws Exception {
        SeatHoldRequest emptySeats = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.emptyList())
                .build();

        mockMvc.perform(post("/api/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptySeats)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/bookings/hold - CUSTOMER can release their hold (200 OK)")
    void shouldAllowCustomerToReleaseHold() throws Exception {
        // Hold seat
        SeatHoldRequest holdReq = SeatHoldRequest.builder()
                .eventId(sampleEvent.getId())
                .eventSeatIds(Collections.singletonList(seatInstance1.getId()))
                .build();
        SeatHoldResponse holdResp = seatHoldService.holdSeats(holdReq, customerUser.getId());

        SeatReleaseRequest releaseReq = SeatReleaseRequest.builder()
                .eventId(sampleEvent.getId())
                .holdToken(holdResp.getHoldToken())
                .build();

        mockMvc.perform(delete("/api/bookings/hold")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.releasedCount", is(1)));
    }

    @Test
    @DisplayName("DELETE /api/bookings/hold - ORGANIZER should be REJECTED (403 Forbidden)")
    void shouldRejectOrganizerFromReleasingHold() throws Exception {
        SeatReleaseRequest releaseReq = SeatReleaseRequest.builder()
                .eventId(sampleEvent.getId())
                .holdToken("some-token")
                .build();

        mockMvc.perform(delete("/api/bookings/hold")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseReq)))
                .andExpect(status().isForbidden());
    }
}

