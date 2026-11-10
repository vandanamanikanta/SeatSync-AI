package com.seatsync.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.common.security.JwtTokenProvider;
import com.seatsync.common.security.UserPrincipal;
import com.seatsync.modules.ai.dto.AiChatRequest;
import com.seatsync.modules.ai.dto.ExtractedEventFilters;
import com.seatsync.modules.ai.service.AiEventAssistantService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiEventAssistantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiEventAssistantService aiEventAssistantService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventCategoryRepository eventCategoryRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String customerJwtToken;
    private User testCustomer;
    private Event concertEvent;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_CUSTOMER").description("Customer").build()));

        testCustomer = userRepository.findByEmail("ai_test_customer@seatsync.ai")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("ai_test_customer@seatsync.ai")
                        .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                        .fullName("AI Tester")
                        .phoneNumber("+919999988888")
                        .status(UserStatus.ACTIVE)
                        .roles(Set.of(customerRole))
                        .build()));

        UserPrincipal principal = UserPrincipal.create(testCustomer);
        customerJwtToken = jwtTokenProvider.generateAccessToken(principal);

        EventCategory musicCategory = eventCategoryRepository.findBySlug("concerts-music")
                .orElseGet(() -> eventCategoryRepository.save(EventCategory.builder()
                        .name("Concerts & Music")
                        .slug("concerts-music")
                        .description("Live music concerts")
                        .iconName("Music")
                        .isActive(true)
                        .build()));

        Venue mumbaiVenue = venueRepository.findBySlug("mumbai-grand-arena-ai")
                .orElseGet(() -> venueRepository.save(Venue.builder()
                        .name("Mumbai Grand Arena AI")
                        .slug("mumbai-grand-arena-ai")
                        .address("BKC Complex")
                        .city("Mumbai")
                        .state("Maharashtra")
                        .country("India")
                        .postalCode("400051")
                        .capacity(10000)
                        .timezone("Asia/Kolkata")
                        .build()));

        concertEvent = eventRepository.findBySlug("rock-revolution-ai-test")
                .orElseGet(() -> eventRepository.save(Event.builder()
                        .title("Rock Revolution AI Test")
                        .slug("rock-revolution-ai-test")
                        .shortDescription("Live stadium rock show")
                        .fullDescription("Complete stadium rock concert experience")
                        .category(musicCategory)
                        .venue(mumbaiVenue)
                        .organizer(testCustomer)
                        .basePrice(new BigDecimal("1999.00"))
                        .status(EventStatus.PUBLISHED)
                        .startTime(Instant.now().plus(14, ChronoUnit.DAYS))
                        .endTime(Instant.now().plus(14, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                        .build()));
    }

    @Test
    @DisplayName("Should extract structured search parameters from natural language query")
    void testNaturalLanguageQueryExtraction() {
        ExtractedEventFilters filters = aiEventAssistantService.extractFilters("Find music concerts in Mumbai under ₹2,000");

        assertThat(filters).isNotNull();
        assertThat(filters.getCategory()).isEqualToIgnoringCase("concerts-music");
        assertThat(filters.getCity()).isEqualToIgnoringCase("Mumbai");
        assertThat(filters.getMaxBudget()).isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    @DisplayName("POST /api/ai/chat should return grounded database events and AI answer")
    void testEventRetrievalAndGroundedResponse() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .message("Find music concerts in Mumbai under 2500")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.grounded").value(true))
                .andExpect(jsonPath("$.data.response").isNotEmpty())
                .andExpect(jsonPath("$.data.totalMatches", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.events", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.events[0].title").isNotEmpty())
                .andExpect(jsonPath("$.data.events[0].city").value("Mumbai"));
    }

    @Test
    @DisplayName("POST /api/ai/chat should handle no-result queries gracefully without hallucination")
    void testNoResultQueryHandling() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .message("Find comedy events in Hyderabad this weekend")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalMatches").value(0))
                .andExpect(jsonPath("$.data.events", hasSize(0)))
                .andExpect(jsonPath("$.data.response", containsString("no events matching")));
    }

    @Test
    @DisplayName("POST /api/ai/chat should reject blank messages with 400 Bad Request")
    void testInvalidBlankMessageRejection() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .message("   ")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/ai/chat should reject unauthenticated requests with 401 Unauthorized")
    void testUnauthenticatedAccessRejection() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .message("Which upcoming events are available?")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/ai/status should return Gemini connectivity status")
    void testAiStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.liveGeminiActive").isBoolean());
    }
}
