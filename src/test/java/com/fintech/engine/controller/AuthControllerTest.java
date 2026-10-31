package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.AuthResponse;
import com.fintech.engine.dto.LoginRequest;
import com.fintech.engine.dto.RegisterRequest;
import com.fintech.engine.exception.EmailAlreadyExistsException;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.exception.InvalidCredentialsException;
import com.fintech.engine.service.AuthService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/v1/auth/register - Should register customer and return 201 Created")
    void shouldRegisterCustomerSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "john@example.com", "Password123!", "John", "Doe", "USD"
        );

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        AuthResponse response = new AuthResponse(
                "Bearer", "access-token-123", 86400, userId, "john@example.com", "ROLE_USER", walletId, "USD-99887766"
        );

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.defaultAccountId").value(walletId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 409 Conflict when email already registered")
    void shouldReturn409OnDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "existing@example.com", "Password123!", "John", "Doe", "USD"
        );

        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("existing@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Should authenticate and return 200 OK with token")
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest request = new LoginRequest("john@example.com", "Password123!");

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        AuthResponse response = new AuthResponse(
                "Bearer", "access-token-xyz", 86400, userId, "john@example.com", "ROLE_USER", walletId, "USD-99887766"
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-xyz"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Should return 401 Unauthorized on invalid credentials")
    void shouldReturn401OnInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("john@example.com", "WrongPassword");

        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
