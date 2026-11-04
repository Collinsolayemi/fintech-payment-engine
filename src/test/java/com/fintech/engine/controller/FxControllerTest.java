package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.*;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.service.FxService;
import com.fintech.engine.service.IdempotencyService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FxController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class FxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FxService fxService;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("GET /api/v1/fx/rates - Should return live market rates")
    void shouldReturnRates() throws Exception {
        when(fxService.getLiveRates("USD")).thenReturn(List.of(
                new FxRateResponse("USD", "EUR", new BigDecimal("0.925000"), new BigDecimal("1.081081"), OffsetDateTime.now())
        ));

        mockMvc.perform(get("/api/v1/fx/rates?base=USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceCurrency").value("USD"))
                .andExpect(jsonPath("$[0].targetCurrency").value("EUR"))
                .andExpect(jsonPath("$[0].rate").value(0.925));
    }

    @Test
    @DisplayName("POST /api/v1/fx/quote - Should generate guaranteed quote")
    void shouldCreateQuote() throws Exception {
        UUID quoteId = UUID.randomUUID();
        FxQuoteResponse quoteResponse = new FxQuoteResponse(
                quoteId, "USD", "EUR",
                new BigDecimal("100.00"), new BigDecimal("92.15"),
                new BigDecimal("0.921500"), new BigDecimal("0.50"),
                60L, OffsetDateTime.now().plusSeconds(60)
        );

        when(fxService.createQuote(any(), any(FxQuoteRequest.class))).thenReturn(quoteResponse);

        FxQuoteRequest request = new FxQuoteRequest("USD", "EUR", new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/fx/quote")
                        .principal(new TestingAuthenticationToken("user@example.com", "pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quoteId").value(quoteId.toString()))
                .andExpect(jsonPath("$.sourceCurrency").value("USD"))
                .andExpect(jsonPath("$.targetCurrency").value("EUR"))
                .andExpect(jsonPath("$.targetAmount").value(92.15));
    }

    @Test
    @DisplayName("POST /api/v1/fx/execute - Should execute conversion")
    void shouldExecuteExchange() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();

        FxExchangeResponse response = new FxExchangeResponse(
                "FX-20261104-123456",
                sourceAccountId,
                targetAccountId,
                new BigDecimal("100.00"),
                new BigDecimal("92.15"),
                "USD",
                "EUR",
                new BigDecimal("0.921500"),
                new BigDecimal("0.50"),
                OffsetDateTime.now()
        );

        when(fxService.executeQuote(eq("user@example.com"), any(ExecuteFxExchangeRequest.class), any()))
                .thenReturn(response);

        ExecuteFxExchangeRequest request = new ExecuteFxExchangeRequest(quoteId, sourceAccountId, targetAccountId);

        mockMvc.perform(post("/api/v1/fx/execute")
                        .principal(new TestingAuthenticationToken("user@example.com", "pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value("FX-20261104-123456"))
                .andExpect(jsonPath("$.sourceAmount").value(100.00))
                .andExpect(jsonPath("$.targetAmount").value(92.15));
    }
}

