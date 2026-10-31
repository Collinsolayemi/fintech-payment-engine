package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.LedgerEntryCommand;
import com.fintech.engine.dto.LedgerEntryResponse;
import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.exception.UnbalancedLedgerException;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.TransactionStatus;
import com.fintech.engine.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LedgerController.class)
@Import(GlobalExceptionHandler.class)
class LedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LedgerService ledgerService;

    @Test
    @DisplayName("POST /api/v1/ledger/postings - Should execute balanced posting and return 201 Created")
    void shouldExecutePostingSuccessfully() throws Exception {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        LedgerPostingRequest request = new LedgerPostingRequest(
                "REF-001", "idem-001", "Manual posting",
                List.of(
                        new LedgerEntryCommand(accountA, EntryType.DEBIT, new BigDecimal("100.00")),
                        new LedgerEntryCommand(accountB, EntryType.CREDIT, new BigDecimal("100.00"))
                )
        );

        TransactionResponse response = new TransactionResponse(
                UUID.randomUUID(), "REF-001", "idem-001", TransactionStatus.COMPLETED,
                "Manual posting", OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(
                        new LedgerEntryResponse(1L, accountA, "ACC-A", EntryType.DEBIT, new BigDecimal("100.00"), OffsetDateTime.now()),
                        new LedgerEntryResponse(2L, accountB, "ACC-B", EntryType.CREDIT, new BigDecimal("100.00"), OffsetDateTime.now())
                )
        );

        when(ledgerService.postTransaction(any(LedgerPostingRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/ledger/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("REF-001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/ledger/postings - Should return 400 Bad Request on Unbalanced entries")
    void shouldReturn400OnUnbalancedPosting() throws Exception {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        LedgerPostingRequest request = new LedgerPostingRequest(
                "REF-BAD", "idem-bad", "Bad posting",
                List.of(
                        new LedgerEntryCommand(accountA, EntryType.DEBIT, new BigDecimal("100.00")),
                        new LedgerEntryCommand(accountB, EntryType.CREDIT, new BigDecimal("90.00"))
                )
        );

        when(ledgerService.postTransaction(any(LedgerPostingRequest.class))).thenThrow(
                new UnbalancedLedgerException(new BigDecimal("100.00"), new BigDecimal("90.00"))
        );

        mockMvc.perform(post("/api/v1/ledger/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNBALANCED_LEDGER"));
    }

    @Test
    @DisplayName("GET /api/v1/ledger/integrity-check - Should verify global platform balance")
    void shouldVerifyGlobalIntegrity() throws Exception {
        when(ledgerService.verifyGlobalIntegrity()).thenReturn(true);

        mockMvc.perform(get("/api/v1/ledger/integrity-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true));
    }
}

