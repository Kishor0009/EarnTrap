package com.earntrap.backend.security;

import com.earntrap.backend.dto.LoginRequest;
import com.earntrap.backend.dto.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SecurityAndAuthIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String testUsername;
    private String testPassword;
    private String testEmail;
    private String validToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        testUsername = "secuser_" + uniqueId;
        testPassword = "SecPassword123!";
        testEmail = "secuser_" + uniqueId + "@example.com";

        // Register a test user
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setFullName("Security Test User");
        registerReq.setUsername(testUsername);
        registerReq.setEmail(testEmail);
        registerReq.setPassword(testPassword);
        registerReq.setPhone("9876543210");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Log in to obtain a valid JWT token
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsernameOrEmail(testUsername);
        loginReq.setPassword(testPassword);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        validToken = responseNode.get("token").asText();
    }

    // ── Public Endpoints & Health Check Tests ───────────────────────────────

    @Test
    @DisplayName("GET /healthz returns 200 UP without authentication")
    void healthCheck_healthz_unauthenticatedReturns200() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("earntrap-backend"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET / returns 200 UP without authentication")
    void healthCheck_root_unauthenticatedReturns200() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("earntrap-backend"));
    }

    @Test
    @DisplayName("GET /api/levels returns public level catalog without authentication")
    void getLevels_unauthenticatedReturnsPublicCatalog() throws Exception {
        mockMvc.perform(get("/api/levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(60)))
                .andExpect(jsonPath("$[0].levelNumber").value(1))
                .andExpect(jsonPath("$[0].status").value("UNLOCKED"))
                .andExpect(jsonPath("$[1].levelNumber").value(2))
                .andExpect(jsonPath("$[1].status").value("LOCKED"))
                .andExpect(jsonPath("$[45].levelNumber").value(46))
                .andExpect(jsonPath("$[45].status").value("COMING_SOON"));
    }

    // ── Registration & Login Tests ──────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register allows registration without existing session")
    void register_newUser_returns201Created() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Another User");
        req.setUsername("newuser_" + id);
        req.setEmail("newuser_" + id + "@example.com");
        req.setPassword("ValidPass123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("Registration successful")));
    }

    @Test
    @DisplayName("POST /api/auth/register with duplicate username returns 409 Conflict")
    void register_duplicateUsername_returns409Conflict() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Duplicate User");
        req.setUsername(testUsername); // already registered in setUp
        req.setEmail("unique_" + UUID.randomUUID() + "@example.com");
        req.setPassword("ValidPass123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/auth/login with valid credentials returns 200 with JWT")
    void login_validCredentials_returns200AndToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail(testUsername);
        req.setPassword(testPassword);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.message").value("Login successful"));
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong password returns 401 Unauthorized")
    void login_invalidPassword_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail(testUsername);
        req.setPassword("WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── CORS & Preflight Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("OPTIONS preflight from https://earn-trap.vercel.app returns 200 with CORS headers")
    void cors_preflightVercelOrigin_returns200WithHeaders() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://earn-trap.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://earn-trap.vercel.app"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("OPTIONS preflight for protected endpoint returns 200 without authentication")
    void cors_preflightOnProtectedEndpoint_unauthenticatedReturns200() throws Exception {
        mockMvc.perform(options("/api/wallet/me")
                        .header("Origin", "https://earn-trap.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://earn-trap.vercel.app"));
    }

    // ── Protected Endpoints Authentication Tests ────────────────────────────

    @Test
    @DisplayName("GET /api/users/me without token is rejected (403/401)")
    void protectedEndpoint_userProfile_unauthenticatedRejected() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/wallet/me without token is rejected (403/401)")
    void protectedEndpoint_wallet_unauthenticatedRejected() throws Exception {
        mockMvc.perform(get("/api/wallet/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/redemptions/eligibility without token is rejected (403/401)")
    void protectedEndpoint_redemptions_unauthenticatedRejected() throws Exception {
        mockMvc.perform(get("/api/redemptions/eligibility"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/levels/1/complete without token is rejected (403/401)")
    void protectedEndpoint_completeLevel_unauthenticatedRejected() throws Exception {
        mockMvc.perform(post("/api/levels/1/complete"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/progress/me without token is rejected (403/401)")
    void protectedEndpoint_progress_unauthenticatedRejected() throws Exception {
        mockMvc.perform(get("/api/progress/me"))
                .andExpect(status().isForbidden());
    }

    // ── Protected Endpoints With Valid JWT Tests ────────────────────────────

    @Test
    @DisplayName("GET /api/users/me with valid JWT token returns 200 with profile")
    void protectedEndpoint_userProfile_withTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testUsername))
                .andExpect(jsonPath("$.email").value(testEmail));
    }

    @Test
    @DisplayName("GET /api/progress/me with valid JWT token returns 200 with progress")
    void protectedEndpoint_progress_withTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/progress/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLevel").value(1))
                .andExpect(jsonPath("$.points").value(0));
    }

    @Test
    @DisplayName("GET /api/levels with valid JWT token returns 200 with personalized levels")
    void protectedEndpoint_levels_withTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/levels")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(60)))
                .andExpect(jsonPath("$[0].status").value("UNLOCKED"));
    }

    @Test
    @DisplayName("GET /api/wallet/me with valid JWT token returns 200")
    void protectedEndpoint_wallet_withTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/wallet/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    // ── JWT Filter Resilience on Public Endpoints ───────────────────────────

    @Test
    @DisplayName("Public endpoint /healthz succeeds even with invalid/garbage JWT token")
    void publicEndpoint_healthz_withInvalidTokenDoesNotFail() throws Exception {
        mockMvc.perform(get("/healthz")
                        .header("Authorization", "Bearer invalid.garbage.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Public endpoint /api/levels succeeds even with invalid JWT token")
    void publicEndpoint_levels_withInvalidTokenReturnsPublicCatalog() throws Exception {
        mockMvc.perform(get("/api/levels")
                        .header("Authorization", "Bearer expired.or.fake.jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(60)));
    }
}
