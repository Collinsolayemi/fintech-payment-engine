package com.fintech.engine.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.DepositRequest;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.dto.TransactionSummaryResponse;
import com.fintech.engine.exception.UserNotFoundException;
import com.fintech.engine.model.entity.IdempotencyRecord;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.IdempotencyService;
import com.fintech.engine.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "Transactions & Ledger History", description = "Endpoints for querying historical transactions, double-entry breakdown, and statement export")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public TransactionController(
            TransactionService transactionService,
            UserRepository userRepository,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper
    ) {
        this.transactionService = transactionService;
        this.userRepository = userRepository;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get User Transactions", description = "Retrieves recent ledger transaction history for a specified customer ID")
    @ApiResponse(responseCode = "200", description = "List of user transactions returned")
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionSummaryResponse>> getUserTransactions(
            @Parameter(description = "Customer UUID") @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(transactionService.getUserTransactions(userId));
    }

    @Operation(summary = "Get Authenticated Customer Transactions", description = "Retrieves recent ledger transactions for the currently authenticated user")
    @ApiResponse(responseCode = "200", description = "List of current user transactions returned")
    @GetMapping("/me")
    public ResponseEntity<List<TransactionSummaryResponse>> getMyTransactions(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException("Authenticated user record not found"));
        return ResponseEntity.ok(transactionService.getUserTransactions(user.getId()));
    }

    @Operation(summary = "Get Paginated Account Transactions", description = "Retrieves paginated ledger entries for a specific wallet account")
    @ApiResponse(responseCode = "200", description = "Page of account transactions returned")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/account/{accountId}")
    public ResponseEntity<Page<TransactionSummaryResponse>> getAccountTransactions(
            @PathVariable UUID accountId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(transactionService.getAccountTransactions(accountId, pageable));
    }

    @Operation(summary = "Get Transaction By Reference", description = "Fetches complete transaction record including balanced double-entry postings")
    @ApiResponse(responseCode = "200", description = "Transaction found with journal breakdown")
    @ApiResponse(responseCode = "404", description = "Transaction reference not found")
    @GetMapping("/{reference}")
    public ResponseEntity<TransactionResponse> getTransactionByReference(@PathVariable String reference) {
        return ResponseEntity.ok(transactionService.getTransactionByReference(reference));
    }

    @Operation(summary = "Export Account Statement CSV", description = "Generates and downloads a CSV statement of ledger entries for a date range")
    @ApiResponse(responseCode = "200", description = "CSV statement stream returned")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/account/{accountId}/statement")
    public ResponseEntity<byte[]> exportAccountStatement(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        byte[] csvBytes = transactionService.exportAccountStatementCsv(accountId, from, to);
        String filename = "statement-" + accountId + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }

    @Operation(summary = "Direct Wallet Deposit", description = "Funds a user wallet with balanced double-entry accounting (Debit Asset Clearing, Credit User Liability)")
    @ApiResponse(responseCode = "200", description = "Deposit posted successfully")
    @ApiResponse(responseCode = "400", description = "Invalid deposit amount or account not active")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DepositRequest request,
            Authentication authentication
    ) {
        String requestHash = computeRequestHash("/api/v1/transactions/deposit", request);
        String authenticatedEmail = authentication != null ? authentication.getName() : null;

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> cached = idempotencyService.checkOrStart(idempotencyKey, requestHash);
            if (cached.isPresent()) {
                try {
                    TransactionResponse cachedResponse = objectMapper.readValue(
                            cached.get().getResponseBody(), TransactionResponse.class);
                    return ResponseEntity.status(cached.get().getResponseCode()).body(cachedResponse);
                } catch (JsonProcessingException ignored) {}
            }
        }

        try {
            TransactionResponse response = transactionService.deposit(request, idempotencyKey, authenticatedEmail);

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

    private String computeRequestHash(String endpoint, Object requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = endpoint + ":" + (requestBody != null ? requestBody.toString() : "");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
