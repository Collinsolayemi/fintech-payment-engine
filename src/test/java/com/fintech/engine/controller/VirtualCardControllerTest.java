package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.*;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.model.enums.CardBrand;
import com.fintech.engine.model.enums.CardStatus;
import com.fintech.engine.service.VirtualCardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = VirtualCardController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class VirtualCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VirtualCardService virtualCardService;

    @Test
    @DisplayName("POST /api/v1/cards/issue - Should issue a new virtual card")
    void shouldIssueCard() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        VirtualCardResponse response = new VirtualCardResponse(
                cardId, accountId, "USD-11223344", "USD",
                "Alex Morgan", "•••• •••• •••• 4532", "4532",
                11, 2028, CardStatus.ACTIVE, CardBrand.VISA, "VIRTUAL_DEBIT",
                new BigDecimal("5000.00"), true, true, OffsetDateTime.now()
        );

        when(virtualCardService.issueCard(eq("user@example.com"), any(IssueCardRequest.class)))
                .thenReturn(response);

        IssueCardRequest request = new IssueCardRequest(accountId, "Alex Morgan", CardBrand.VISA, new BigDecimal("5000.00"));

        mockMvc.perform(post("/api/v1/cards/issue")
                        .principal(new TestingAuthenticationToken("user@example.com", "pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(cardId.toString()))
                .andExpect(jsonPath("$.cardholderName").value("Alex Morgan"))
                .andExpect(jsonPath("$.cardBrand").value("VISA"));
    }

    @Test
    @DisplayName("GET /api/v1/cards/me - Should return user's masked cards")
    void shouldGetUserCards() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        VirtualCardResponse response = new VirtualCardResponse(
                cardId, accountId, "USD-11223344", "USD",
                "Alex Morgan", "•••• •••• •••• 4532", "4532",
                11, 2028, CardStatus.ACTIVE, CardBrand.VISA, "VIRTUAL_DEBIT",
                new BigDecimal("5000.00"), true, true, OffsetDateTime.now()
        );

        when(virtualCardService.getUserCards("user@example.com")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/cards/me")
                        .principal(new TestingAuthenticationToken("user@example.com", "pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cardId.toString()))
                .andExpect(jsonPath("$[0].maskedCardNumber").value("•••• •••• •••• 4532"));
    }

    @Test
    @DisplayName("GET /api/v1/cards/{id}/details - Should reveal full PAN and CVV")
    void shouldRevealCardDetails() throws Exception {
        UUID cardId = UUID.randomUUID();
        CardDetailsResponse details = new CardDetailsResponse(
                cardId, "4532123456789012", "884", 11, 2028, "Alex Morgan"
        );

        when(virtualCardService.getCardDetails("user@example.com", cardId)).thenReturn(details);

        mockMvc.perform(get("/api/v1/cards/" + cardId + "/details")
                        .principal(new TestingAuthenticationToken("user@example.com", "pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value("4532123456789012"))
                .andExpect(jsonPath("$.cvv").value("884"));
    }

    @Test
    @DisplayName("PATCH /api/v1/cards/{id}/status - Should freeze virtual card")
    void shouldUpdateCardStatus() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        VirtualCardResponse response = new VirtualCardResponse(
                cardId, accountId, "USD-11223344", "USD",
                "Alex Morgan", "•••• •••• •••• 4532", "4532",
                11, 2028, CardStatus.FROZEN, CardBrand.VISA, "VIRTUAL_DEBIT",
                new BigDecimal("5000.00"), true, true, OffsetDateTime.now()
        );

        when(virtualCardService.updateCardStatus(eq("user@example.com"), eq(cardId), any(UpdateCardStatusRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/cards/" + cardId + "/status")
                        .principal(new TestingAuthenticationToken("user@example.com", "pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardStatusRequest(CardStatus.FROZEN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }
}

