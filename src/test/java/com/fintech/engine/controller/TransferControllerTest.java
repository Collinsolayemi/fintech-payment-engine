package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.TransferRequest;
import com.fintech.engine.dto.TransferResponse;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.exception.InsufficientFundsException;
import com.fintech.engine.exception.UnauthorizedAccountAccessException;
import com.fintech.engine.model.entity.IdempotencyRecord;
import com.fintech.engine.model.enums.TransactionStatus;
import com.fintech.engine.service.IdempotencyService;
import com.fintech.engine.service.TransferService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TransferController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferService transferService;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("POST /api/v1/transfers - Should execute transfer and return 200 OK")
    void shouldExecuteTransferSuccessfully() throws Exception {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                senderId, recipientId, new BigDecimal("75.00"), BigDecimal.ZERO, "USD", "Payment"
        );

        TransferResponse expectedResponse = new TransferResponse(
                "TX-20261031-112233", "idem-uuid-1", TransactionStatus.COMPLETED,
                new BigDecimal("75.00"), BigDecimal.ZERO, "USD", senderId, recipientId, "Payment", OffsetDateTime.now()
        );

        when(idempotencyService.checkOrStart(eq("idem-uuid-1"), any())).thenReturn(Optional.empty());
        when(transferService.transfer(any(TransferRequest.class), eq("idem-uuid-1"), any())).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "idem-uuid-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value("TX-20261031-112233"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(75.00))
                .andExpect(jsonPath("$.currency").value("USD"));

        verify(idempotencyService).complete(eq("idem-uuid-1"), eq(200), any());
    }

    @Test
    @DisplayName("POST /api/v1/transfers - Should return cached response on duplicate Idempotency-Key")
    void shouldReturnCachedResponseOnDuplicateIdempotencyKey() throws Exception {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                senderId, recipientId, new BigDecimal("75.00"), BigDecimal.ZERO, "USD", "Payment"
        );

        TransferResponse cachedReceipt = new TransferResponse(
                "TX-CACHED-REF", "idem-uuid-cached", TransactionStatus.COMPLETED,
                new BigDecimal("75.00"), BigDecimal.ZERO, "USD", senderId, recipientId, "Payment", OffsetDateTime.now()
        );
        String cachedJson = objectMapper.writeValueAsString(cachedReceipt);

        IdempotencyRecord cachedRecord = new IdempotencyRecord("idem-uuid-cached", "hash", "COMPLETED", OffsetDateTime.now().plusHours(24));
        cachedRecord.markCompleted(200, cachedJson);

        when(idempotencyService.checkOrStart(eq("idem-uuid-cached"), any())).thenReturn(Optional.of(cachedRecord));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "idem-uuid-cached")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value("TX-CACHED-REF"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(transferService, never()).transfer(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/transfers - Should return 400 Bad Request on Insufficient Funds")
    void shouldReturn400OnInsufficientFunds() throws Exception {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                senderId, recipientId, new BigDecimal("500.00"), BigDecimal.ZERO, "USD", "Too much"
        );

        when(idempotencyService.checkOrStart(any(), any())).thenReturn(Optional.empty());
        when(transferService.transfer(any(), any(), any())).thenThrow(
                new InsufficientFundsException(senderId, new BigDecimal("50.00"), new BigDecimal("500.00"))
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "idem-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        verify(idempotencyService).fail("idem-fail");
    }

    @Test
    @DisplayName("POST /api/v1/transfers - Should return 403 Forbidden when user does not own sender account")
    void shouldReturn403OnUnauthorizedAccountAccess() throws Exception {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                senderId, recipientId, new BigDecimal("50.00"), BigDecimal.ZERO, "USD", "Hack attempt"
        );

        when(idempotencyService.checkOrStart(any(), any())).thenReturn(Optional.empty());
        when(transferService.transfer(any(), any(), any())).thenThrow(
                new UnauthorizedAccountAccessException(senderId, "attacker@example.com")
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "idem-unauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCOUNT_ACCESS"));
    }

    @Test
    @DisplayName("GET /api/v1/transfers/{ref} - Should return transfer details")
    void shouldReturnTransferDetails() throws Exception {
        TransferResponse response = new TransferResponse(
                "TX-REF-100", null, TransactionStatus.COMPLETED,
                new BigDecimal("30.00"), BigDecimal.ZERO, "USD", null, null, "Details", OffsetDateTime.now()
        );

        when(transferService.getTransferByReference("TX-REF-100")).thenReturn(response);

        mockMvc.perform(get("/api/v1/transfers/TX-REF-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value("TX-REF-100"));
    }
}
