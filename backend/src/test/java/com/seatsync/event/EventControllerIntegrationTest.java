package com.seatsync.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.common.security.JwtTokenProvider;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.event.dto.CreateEventRequest;
import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventCategory;
import com.seatsync.modules.event.entity.EventStatus;
import com.seatsync.modules.event.entity.Venue;
import com.seatsync.modules.event.repository.EventCategoryRepository;
import com.seatsync.modules.event.repository.EventRepository;
import com.seatsync.modules.event.repository.VenueRepository;
import com.seatsync.modules.user.entity.Role;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.entity.UserStatus;
import com.seatsync.modules.user.repository.RoleRepository;
import com.seatsync.modules.user.repository.UserRepository;
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
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired(required = false)
    private com.seatsync.modules.booking.repository.EventSeatRepository eventSeatRepository;

    @Autowired(required = false)
    private com.seatsync.modules.event.repository.SeatRepository seatRepository;

    @Autowired(required = false)
    private com.seatsync.modules.event.repository.VenueSectionRepository venueSectionRepository;

    @Autowired
    private EventCategoryRepository categoryRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User organizerUser;
    private User customerUser;
    private String organizerToken;
    private String customerToken;
    private EventCategory musicCategory;
    private Venue stadiumVenue;
    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        if (eventSeatRepository != null) eventSeatRepository.deleteAll();
        if (seatRepository != null) seatRepository.deleteAll();
        if (venueSectionRepository != null) venueSectionRepository.deleteAll();
        eventRepository.deleteAll();
        categoryRepository.deleteAll();
        venueRepository.deleteAll();
        userRepository.deleteAll();

        Role roleOrganizer = roleRepository.findByName("ROLE_ORGANIZER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ORGANIZER", "Organizer")));
        Role roleCustomer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER", "Customer")));

        organizerUser = userRepository.save(User.builder()
                .email("org.test@seatsync.ai")
                .passwordHash("hashed")
                .fullName("Organizer Test")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(roleOrganizer))
                .build());

        customerUser = userRepository.save(User.builder()
                .email("cust.test@seatsync.ai")
                .passwordHash("hashed")
                .fullName("Customer Test")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(roleCustomer))
                .build());

        organizerToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(organizerUser));
        customerToken = jwtTokenProvider.generateAccessToken(UserPrincipal.create(customerUser));

        musicCategory = categoryRepository.save(EventCategory.builder()
                .name("Concerts & Music")
                .slug("concerts-music")
                .displayOrder(1)
                .isActive(true)
                .build());

        stadiumVenue = venueRepository.save(Venue.builder()
                .name("Gachibowli Stadium")
                .slug("gachibowli-stadium-hyderabad")
                .address("Gachibowli")
                .city("Hyderabad")
                .state("Telangana")
                .postalCode("500032")
                .capacity(20000)
                .build());

        Instant now = Instant.now();
        sampleEvent = eventRepository.save(Event.builder()
                .organizer(organizerUser)
                .category(musicCategory)
                .venue(stadiumVenue)
                .title("Arijit Singh Symphony Live 2026")
                .slug("arijit-singh-symphony-live-2026")
                .shortDescription("A musical symphony experience live in Hyderabad")
                .fullDescription("Complete details about the concert tour and artist lineup.")
                .startTime(now.plus(7, ChronoUnit.DAYS))
                .endTime(now.plus(7, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS))
                .basePrice(new BigDecimal("1499.00"))
                .status(EventStatus.PUBLISHED)
                .build());
    }

    @Test
    @DisplayName("GET /api/events should return paginated published events without authentication")
    void shouldReturnPublishedEventsPublicly() throws Exception {
        mockMvc.perform(get("/api/events")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].title", is("Arijit Singh Symphony Live 2026")))
                .andExpect(jsonPath("$.data.content[0].venue.city", is("Hyderabad")));
    }

    @Test
    @DisplayName("GET /api/events?city=Hyderabad should filter events by venue city")
    void shouldFilterEventsByCity() throws Exception {
        mockMvc.perform(get("/api/events?city=Hyderabad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].venue.city", is("Hyderabad")));

        mockMvc.perform(get("/api/events?city=Mumbai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/events/{id} should return event details by ID")
    void shouldGetEventDetailsById() throws Exception {
        mockMvc.perform(get("/api/events/" + sampleEvent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(sampleEvent.getId().intValue())))
                .andExpect(jsonPath("$.data.slug", is("arijit-singh-symphony-live-2026")))
                .andExpect(jsonPath("$.data.category.slug", is("concerts-music")));
    }

    @Test
    @DisplayName("POST /api/events should allow ORGANIZER to create event")
    void shouldAllowOrganizerToCreateEvent() throws Exception {
        Instant startTime = Instant.now().plus(14, ChronoUnit.DAYS);
        Instant endTime = startTime.plus(3, ChronoUnit.HOURS);

        CreateEventRequest request = CreateEventRequest.builder()
                .categoryId(musicCategory.getId())
                .venueId(stadiumVenue.getId())
                .title("Coldplay Music of the Spheres Hyderabad")
                .shortDescription("Global stadium world tour live")
                .startTime(startTime)
                .endTime(endTime)
                .basePrice(new BigDecimal("2500.00"))
                .status(EventStatus.PUBLISHED)
                .build();

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.title", is("Coldplay Music of the Spheres Hyderabad")))
                .andExpect(jsonPath("$.data.organizer.id", is(organizerUser.getId().intValue())));
    }

    @Test
    @DisplayName("POST /api/events should reject CUSTOMER role with 403 Forbidden")
    void shouldRejectCustomerCreatingEvent() throws Exception {
        Instant startTime = Instant.now().plus(14, ChronoUnit.DAYS);
        Instant endTime = startTime.plus(3, ChronoUnit.HOURS);

        CreateEventRequest request = CreateEventRequest.builder()
                .categoryId(musicCategory.getId())
                .venueId(stadiumVenue.getId())
                .title("Unauthorized Customer Concert")
                .shortDescription("Should be forbidden")
                .startTime(startTime)
                .endTime(endTime)
                .basePrice(new BigDecimal("1000.00"))
                .build();

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("PATCH /api/events/{id}/status should update event status")
    void shouldUpdateEventStatus() throws Exception {
        mockMvc.perform(patch("/api/events/" + sampleEvent.getId() + "/status?status=POSTPONED")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("POSTPONED")));
    }

    @Test
    @DisplayName("GET /api/categories should return active categories")
    void shouldReturnActiveCategories() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].slug", is("concerts-music")));
    }

    @Test
    @DisplayName("GET /api/venues should return venues")
    void shouldReturnVenues() throws Exception {
        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].name", is("Gachibowli Stadium")));
    }
}

