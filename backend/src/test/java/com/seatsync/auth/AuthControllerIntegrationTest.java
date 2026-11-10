package com.seatsync.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.modules.auth.dto.LoginRequest;
import com.seatsync.modules.auth.dto.RegisterRequest;
import com.seatsync.modules.user.entity.Role;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        if (!roleRepository.existsByName("ROLE_CUSTOMER")) {
            roleRepository.save(new Role("ROLE_CUSTOMER", "Customer role"));
        }
        if (!roleRepository.existsByName("ROLE_ORGANIZER")) {
            roleRepository.save(new Role("ROLE_ORGANIZER", "Organizer role"));
        }
    }

    @Test
    @DisplayName("POST /api/auth/register should successfully register a new customer and return JWT")
    void shouldRegisterNewUser() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("newcustomer@seatsync.ai")
                .password("Password123!")
                .fullName("New Customer")
                .phoneNumber("+919123456789")
                .role("ROLE_CUSTOMER")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("User registered successfully")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is("newcustomer@seatsync.ai")))
                .andExpect(jsonPath("$.data.user.roles", hasItem("ROLE_CUSTOMER")));
    }

    @Test
    @DisplayName("POST /api/auth/register should return 400 when validation fails on email or password")
    void shouldFailValidationOnBadRegisterPayload() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("not-a-valid-email")
                .password("short")
                .fullName("")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.error.details", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("POST /api/auth/register should return 409 Conflict if email is already registered")
    void shouldReturnConflictOnDuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("duplicate@seatsync.ai")
                .password("Password123!")
                .fullName("First User")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Attempt registering duplicate email
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("POST /api/auth/login should authenticate registered user and return tokens")
    void shouldLoginSuccessfully() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("loginuser@seatsync.ai")
                .password("ValidPass123!")
                .fullName("Login User")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("loginuser@seatsync.ai")
                .password("ValidPass123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is("loginuser@seatsync.ai")));
    }

    @Test
    @DisplayName("POST /api/auth/login should return 401 when password is wrong")
    void shouldRejectWrongPassword() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("userpass@seatsync.ai")
                .password("ValidPass123!")
                .fullName("User Pass")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("userpass@seatsync.ai")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("GET /api/auth/me should return current user profile with valid JWT Bearer token")
    void shouldReturnCurrentUserWithBearerToken() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("profileuser@seatsync.ai")
                .password("ValidPass123!")
                .fullName("Profile User")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = registerResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseJson).path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is("profileuser@seatsync.ai")))
                .andExpect(jsonPath("$.data.fullName", is("Profile User")));
    }

    @Test
    @DisplayName("GET /api/auth/me should return 401 when Authorization header is missing")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }
}

