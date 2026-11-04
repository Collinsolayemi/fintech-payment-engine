package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.DepositRequest;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.dto.TransactionSummaryResponse;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.exception.TransactionNotFoundException;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.TransactionStatus;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.IdempotencyService;
import com.fintech.engine.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TransactionController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("GET /api/v1/transactions/user/{userId} - Should return list of transactions")
    void shouldReturnUserTransactions() throws Exception {
        UUID userId = UUID.randomUUID();
        TransactionSummaryResponse summary = new TransactionSummaryResponse(
                UUID.randomUUID(),
                "TX-20261104-001",
                "Dinner split",
                "COMPLETED",
                new BigDecimal("-25.00"),
                "USD",
                EntryType.DEBIT,
                UUID.randomUUID(),
                "USD-11223344",
                OffsetDateTime.now()
        );

        when(transactionService.getUserTransactions(userId)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/transactions/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value("TX-20261104-001"))
                .andExpect(jsonPath("$[0].amount").value(-25.00))
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/me - Should return transactions for authenticated user")
    @WithMockUser(username = "alex@fintech.com")
    void shouldReturnMyTransactions() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = new User("alex@fintech.com", "hash", "Alex", "Morgan");
        mockUser.setId(userId);

        TransactionSummaryResponse summary = new TransactionSummaryResponse(
                UUID.randomUUID(),
                "TX-ME-001",
                "Freelance payout",
                "COMPLETED",
                new BigDecimal("500.00"),
                "USD",
                EntryType.CREDIT,
                UUID.randomUUID(),
                "USD-99887766",
                OffsetDateTime.now()
        );

        when(userRepository.findByEmail("alex@fintech.com")).thenReturn(Optional.of(mockUser));
        when(transactionService.getUserTransactions(userId)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/transactions/me")
                        .principal(new org.springframework.security.authentication.TestingAuthenticationToken("alex@fintech.com", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value("TX-ME-001"))
                .andExpect(jsonPath("$[0].amount").value(500.00));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{reference} - Should return transaction detail")
    void shouldReturnTransactionByReference() throws Exception {
        TransactionResponse response = new TransactionResponse(
                UUID.randomUUID(),
                "TX-FIND-001",
                "IDEM-1",
                TransactionStatus.COMPLETED,
                "Transfer payment",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of()
        );

        when(transactionService.getTransactionByReference("TX-FIND-001")).thenReturn(response);

        mockMvc.perform(get("/api/v1/transactions/{reference}", "TX-FIND-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("TX-FIND-001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{reference} - Should return 404 if not found")
    void shouldReturn404WhenNotFound() throws Exception {
        when(transactionService.getTransactionByReference("TX-UNKNOWN"))
                .thenThrow(new TransactionNotFoundException("TX-UNKNOWN"));

        mockMvc.perform(get("/api/v1/transactions/{reference}", "TX-UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transaction Not Found"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/account/{accountId}/statement - Should stream CSV file")
    void shouldDownloadStatementCsv() throws Exception {
        UUID accountId = UUID.randomUUID();
        String csvData = "Timestamp,Transaction Reference,Type,Amount,Currency,Account Number,Description,Status\n" +
                "2026-11-04T08:00:00Z,TX-01,CREDIT,100.00,USD,USD-123,Test,COMPLETED\n";

        when(transactionService.exportAccountStatementCsv(eq(accountId), any(), any()))
                .thenReturn(csvData.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/transactions/account/{accountId}/statement", accountId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"statement-" + accountId + ".csv\""))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(csvData));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/deposit - Should process deposit request")
    void shouldProcessDeposit() throws Exception {
        UUID accountId = UUID.randomUUID();
        DepositRequest request = new DepositRequest(accountId, new BigDecimal("150.00"), "USD", "Direct deposit");

        TransactionResponse response = new TransactionResponse(
                UUID.randomUUID(),
                "DEP-20261104-884920",
                "IDEM-DEP-2",
                TransactionStatus.COMPLETED,
                "Direct deposit",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of()
        );

        when(idempotencyService.checkOrStart(any(), any())).thenReturn(Optional.empty());
        when(transactionService.deposit(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "IDEM-DEP-2")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("DEP-20261104-884920"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
