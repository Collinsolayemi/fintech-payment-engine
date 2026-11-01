package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.KycApplicationResponse;
import com.fintech.engine.dto.ReviewKycRequest;
import com.fintech.engine.dto.UserProfileResponse;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.model.enums.*;
import com.fintech.engine.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminUserController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import({GlobalExceptionHandler.class, AdminUserControllerTest.MethodSecurityTestConfig.class})
class AdminUserControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Non-admin user should receive 403 Forbidden on admin endpoints")
    void nonAdminShouldBeForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin user should list users successfully")
    void adminShouldListUsers() throws Exception {
        UserProfileResponse user = new UserProfileResponse(
                UUID.randomUUID(), "customer@example.com", "John", "Doe", "ROLE_USER",
                1, "Basic (Email Verified)", new BigDecimal("500.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, new BigDecimal("1000.00"), KycStatus.APPROVED,
                null, null, null, null, null, null, OffsetDateTime.now(), OffsetDateTime.now());

        when(userService.listUsers(any(), any())).thenReturn(new PageImpl<>(List.of(user)));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("customer@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin user should review and approve KYC application")
    void adminShouldApproveKycApplication() throws Exception {
        UUID appId = UUID.randomUUID();
        ReviewKycRequest request = new ReviewKycRequest(KycApplicationStatus.APPROVED, null);

        KycApplicationResponse response = new KycApplicationResponse(
                appId, UUID.randomUUID(), "customer@example.com", 2, IdType.PASSPORT,
                "A12345", null, KycApplicationStatus.APPROVED, null, "admin",
                OffsetDateTime.now(), OffsetDateTime.now());

        when(userService.reviewKycApplication(eq(appId), any(ReviewKycRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/users/kyc/applications/" + appId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.targetTier").value(2));
    }
}

