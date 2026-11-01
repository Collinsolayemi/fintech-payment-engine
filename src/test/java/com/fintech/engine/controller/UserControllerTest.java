package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.*;
import com.fintech.engine.exception.DuplicateKycApplicationException;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.exception.InvalidKycTierException;
import com.fintech.engine.model.enums.*;
import com.fintech.engine.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("GET /api/v1/users/me - Should return profile details")
    void shouldReturnProfile() throws Exception {
        UserProfileResponse profile = new UserProfileResponse(
                UUID.randomUUID(), "user@example.com", "John", "Doe", "ROLE_USER",
                1, "Basic (Email Verified)", new BigDecimal("500.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, new BigDecimal("1000.00"), KycStatus.APPROVED,
                "+123456", "100 Broadway", "New York", "USA", "10005",
                null, OffsetDateTime.now(), OffsetDateTime.now());

        when(userService.getProfile(any())).thenReturn(profile);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.kycTier").value(1))
                .andExpect(jsonPath("$.singleTransactionLimit").value(500.00));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me - Should update profile")
    void shouldUpdateProfile() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Jane", "Doe", "+987654321", "456 Market St", "San Francisco", "USA", "94103", null);

        UserProfileResponse updated = new UserProfileResponse(
                UUID.randomUUID(), "user@example.com", "Jane", "Doe", "ROLE_USER",
                1, "Basic (Email Verified)", new BigDecimal("500.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, new BigDecimal("1000.00"), KycStatus.APPROVED,
                "+987654321", "456 Market St", "San Francisco", "USA", "94103",
                null, OffsetDateTime.now(), OffsetDateTime.now());

        when(userService.updateProfile(any(), any(UpdateProfileRequest.class))).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.city").value("San Francisco"));
    }

    @Test
    @DisplayName("POST /api/v1/users/kyc/upgrade - Should submit KYC request and return 201 Created")
    void shouldSubmitKycApplication() throws Exception {
        SubmitKycRequest request = new SubmitKycRequest(2, IdType.PASSPORT, "P123456", "https://docs.example.com/p.jpg");

        KycApplicationResponse response = new KycApplicationResponse(
                UUID.randomUUID(), UUID.randomUUID(), "user@example.com", 2, IdType.PASSPORT,
                "P123456", "https://docs.example.com/p.jpg", KycApplicationStatus.SUBMITTED,
                null, null, OffsetDateTime.now(), OffsetDateTime.now());

        when(userService.submitKycApplication(any(), any(SubmitKycRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/users/kyc/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetTier").value(2))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("POST /api/v1/users/kyc/upgrade - Should return 400 on invalid tier jump")
    void shouldReturn400OnInvalidTier() throws Exception {
        SubmitKycRequest request = new SubmitKycRequest(3, IdType.PASSPORT, "P123456", null);

        when(userService.submitKycApplication(any(), any(SubmitKycRequest.class)))
                .thenThrow(new InvalidKycTierException("Tier progression must be sequential"));

        mockMvc.perform(post("/api/v1/users/kyc/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_KYC_TIER"));
    }

    @Test
    @DisplayName("POST /api/v1/users/kyc/upgrade - Should return 409 on duplicate pending request")
    void shouldReturn409OnDuplicateApplication() throws Exception {
        SubmitKycRequest request = new SubmitKycRequest(2, IdType.PASSPORT, "P123456", null);

        when(userService.submitKycApplication(any(), any(SubmitKycRequest.class)))
                .thenThrow(new DuplicateKycApplicationException("An active KYC verification application is already under review"));

        mockMvc.perform(post("/api/v1/users/kyc/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_KYC_APPLICATION"));
    }
}

