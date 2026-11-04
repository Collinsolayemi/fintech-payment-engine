package com.fintech.engine.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.*;
import com.fintech.engine.model.entity.IdempotencyRecord;
import com.fintech.engine.service.FxService;
import com.fintech.engine.service.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Tag(name = "Foreign Exchange (FX)", description = "Endpoints for live currency rates, guaranteed quotation, and multi-currency ledger conversions")
@RestController
@RequestMapping("/api/v1/fx")
public class FxController {

    private final FxService fxService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public FxController(FxService fxService, IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.fxService = fxService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get Live FX Rates", description = "Retrieves live market rates and spreads relative to a base currency (default USD)")
    @ApiResponse(responseCode = "200", description = "FX rates retrieved successfully")
    @GetMapping("/rates")
    public ResponseEntity<List<FxRateResponse>> getLiveRates(
            @Parameter(description = "Base currency (USD, EUR, GBP, CAD, etc.)")
            @RequestParam(required = false, defaultValue = "USD") String base) {
        return ResponseEntity.ok(fxService.getLiveRates(base));
    }

    @Operation(summary = "Request Guaranteed FX Quote", description = "Locks in a guaranteed conversion rate with 60-second TTL")
    @ApiResponse(responseCode = "201", description = "FX quote generated")
    @ApiResponse(responseCode = "400", description = "Invalid currency pair or amount")
    @PostMapping("/quote")
    public ResponseEntity<FxQuoteResponse> createQuote(
            @Valid @RequestBody FxQuoteRequest request,
            Authentication authentication) {
        String userEmail = extractEmail(authentication);
        FxQuoteResponse response = fxService.createQuote(userEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Execute Currency Exchange", description = "Executes the guaranteed FX quote, executing atomic double-entry postings across multi-currency accounts")
    @ApiResponse(responseCode = "200", description = "Exchange executed successfully")
    @ApiResponse(responseCode = "400", description = "Insufficient funds or invalid accounts")
    @ApiResponse(responseCode = "404", description = "Quote or account not found")
    @ApiResponse(responseCode = "410", description = "Quote expired")
    @PostMapping("/execute")
    public ResponseEntity<FxExchangeResponse> executeExchange(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExecuteFxExchangeRequest request,
            Authentication authentication) {
        String userEmail = extractEmail(authentication);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> cached = idempotencyService.checkOrStart(idempotencyKey, request.quoteId().toString());
            if (cached.isPresent()) {
                try {
                    FxExchangeResponse cachedResponse = objectMapper.readValue(
                            cached.get().getResponseBody(), FxExchangeResponse.class);
                    return ResponseEntity.status(cached.get().getResponseCode()).body(cachedResponse);
                } catch (JsonProcessingException ignored) {}
            }
        }

        try {
            FxExchangeResponse response = fxService.executeQuote(userEmail, request, idempotencyKey);

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                try {
                    String json = objectMapper.writeValueAsString(response);
                    idempotencyService.complete(idempotencyKey, HttpStatus.OK.value(), json);
                } catch (JsonProcessingException ignored) {}
            }

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.fail(idempotencyKey);
            }
            throw e;
        }
    }

    @Operation(summary = "Get User FX History", description = "Retrieves recent FX quotes and conversions for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Quote history retrieved")
    @GetMapping("/quotes/me")
    public ResponseEntity<List<FxQuoteResponse>> getUserQuotes(Authentication authentication) {
        String userEmail = extractEmail(authentication);
        return ResponseEntity.ok(fxService.getUserQuotes(userEmail));
    }

    private String extractEmail(Authentication authentication) {
        if (authentication == null) {
            return "user@example.com";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return authentication.getName();
    }
}
