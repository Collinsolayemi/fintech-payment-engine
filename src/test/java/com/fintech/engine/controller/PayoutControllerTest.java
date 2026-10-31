package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.InitiatePayoutRequest;
import com.fintech.engine.dto.PayoutResponse;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.exception.InsufficientFundsException;
import com.fintech.engine.model.enums.PayoutStatus;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.IdempotencyService;
import com.fintech.engine.service.PayoutService;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PayoutController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class PayoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PayoutService payoutService;

    @MockBean
    private IdempotencyService idempotencyService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/v1/payouts - Should initiate payout and return 200 OK")
    void shouldInitiatePayoutSuccessfully() throws Exception {
        UUID accountId = UUID.randomUUID();
        InitiatePayoutRequest request = new InitiatePayoutRequest(
                accountId, new BigDecimal("100.00"), "USD", "CHASE001", "1234567890", "Jane Doe", "Withdrawal"
        );

        PayoutResponse response = new PayoutResponse(
                UUID.randomUUID(), "PO-20261031-112233", accountId, "USD-USER-1",
                new BigDecimal("100.00"), new BigDecimal("2.00"), "USD",
                PayoutStatus.PROCESSING, "CHASE001", "1234567890", "Jane Doe",
                null, OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(idempotencyService.checkOrStart(any(), any())).thenReturn(Optional.empty());
        when(payoutService.initiatePayout(any(InitiatePayoutRequest.class), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payouts")
                        .header("Idempotency-Key", "idemp-payout-999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("PO-20261031-112233"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("POST /api/v1/payouts - Should return 400 Bad Request on insufficient balance")
    void shouldReturn400OnInsufficientFunds() throws Exception {
        UUID accountId = UUID.randomUUID();
        InitiatePayoutRequest request = new InitiatePayoutRequest(
                accountId, new BigDecimal("500.00"), "USD", "CHASE001", "1234567890", "Jane Doe", "Withdrawal"
        );

        when(idempotencyService.checkOrStart(any(), any())).thenReturn(Optional.empty());
        when(payoutService.initiatePayout(any(), any(), any()))
                .thenThrow(new InsufficientFundsException(accountId, new BigDecimal("20.00"), new BigDecimal("502.00")));

        mockMvc.perform(post("/api/v1/payouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("GET /api/v1/payouts/{reference} - Should return payout details")
    void shouldGetPayoutByReference() throws Exception {
        PayoutResponse response = new PayoutResponse(
                UUID.randomUUID(), "PO-REF-100", UUID.randomUUID(), "USD-1",
                new BigDecimal("50.00"), new BigDecimal("2.00"), "USD",
                PayoutStatus.COMPLETED, "BANK", "123", "User", null,
                OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(payoutService.getPayoutByReference("PO-REF-100")).thenReturn(response);

        mockMvc.perform(get("/api/v1/payouts/PO-REF-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("PO-REF-100"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}

