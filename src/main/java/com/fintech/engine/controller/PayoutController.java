package com.fintech.engine.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.InitiatePayoutRequest;
import com.fintech.engine.dto.PayoutResponse;
import com.fintech.engine.dto.ProcessPayoutCallbackRequest;
import com.fintech.engine.exception.UserNotFoundException;
import com.fintech.engine.model.entity.IdempotencyRecord;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.IdempotencyService;
import com.fintech.engine.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Tag(name = "Outbound Payouts", description = "Endpoints for initiating bank withdrawals, holding funds in two-phase escrow, and managing bank settlement flows")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/api/v1/payouts")
public class PayoutController {

    private final PayoutService payoutService;
    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PayoutController(
        PayoutService payoutService,
        IdempotencyService idempotencyService,
        UserRepository userRepository,
        ObjectMapper objectMapper
    ) {
        this.payoutService = payoutService;
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Initiate Outbound Payout (Withdrawal)", description = "Debits user available balance, moves funds into platform pending escrow (SYS-2002), and records payout.")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, description = "Unique UUIDv4 idempotency key to prevent duplicate payouts",
               required = false, schema = @Schema(type = "string", format = "uuid"))
    @ApiResponse(responseCode = "200", description = "Payout initiated and held in escrow")
    @ApiResponse(responseCode = "400", description = "Insufficient funds or invalid bank destination")
    @ApiResponse(responseCode = "403", description = "User does not own the source wallet")
    @PostMapping({"", "/initiate"})
    public ResponseEntity<PayoutResponse> initiatePayout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InitiatePayoutRequest request,
            Authentication authentication
    ) {
        String requestHash = computeRequestHash("/api/v1/payouts", request);
        String authenticatedEmail = authentication != null ? authentication.getName() : null;

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> cached = idempotencyService.checkOrStart(idempotencyKey, requestHash);
            if (cached.isPresent()) {
                try {
                    PayoutResponse cachedResponse = objectMapper.readValue(cached.get().getResponseBody(), PayoutResponse.class);
                    return ResponseEntity.status(cached.get().getResponseCode()).body(cachedResponse);
                } catch (JsonProcessingException ignored) {}
            }
        }

        try {
            PayoutResponse response = payoutService.initiatePayout(request, idempotencyKey, authenticatedEmail);

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

    @Operation(summary = "Get Payout Receipt", description = "Fetches withdrawal details by unique reference")
    @ApiResponse(responseCode = "200", description = "Payout found")
    @ApiResponse(responseCode = "404", description = "Payout reference not found")
    @GetMapping("/{reference}")
    public ResponseEntity<PayoutResponse> getPayout(@PathVariable String reference) {
        return ResponseEntity.ok(payoutService.getPayoutByReference(reference));
    }

    @Operation(summary = "List Current User Payouts", description = "Retrieves all withdrawal requests for the authenticated customer")
    @ApiResponse(responseCode = "200", description = "Payout history returned")
    @GetMapping({"/me", "/my-payouts"})
    public ResponseEntity<List<PayoutResponse>> getMyPayouts(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return ResponseEntity.ok(payoutService.getUserPayouts(user.getId()));
    }

    @Operation(summary = "Complete Payout Settlement (Phase 2A)", description = "Settles funds from platform escrow account to clearing bank account.")
    @ApiResponse(responseCode = "200", description = "Payout settled successfully")
    @ApiResponse(responseCode = "400", description = "Payout already settled or reversed")
    @PostMapping("/{reference}/settle")
    public ResponseEntity<PayoutResponse> completePayout(@PathVariable String reference) {
        return ResponseEntity.ok(payoutService.completePayout(reference));
    }

    @Operation(summary = "Reverse Payout Settlement (Phase 2B)", description = "Releases funds from escrow and returns full principal + fee back to the user.")
    @ApiResponse(responseCode = "200", description = "Payout reversed and refunded")
    @ApiResponse(responseCode = "400", description = "Payout cannot be reversed")
    @PostMapping("/{reference}/reverse")
    public ResponseEntity<PayoutResponse> reversePayout(
            @PathVariable String reference,
            @RequestBody(required = false) ProcessPayoutCallbackRequest request
    ) {
        String reason = (request != null && request.failureReason() != null) ? request.failureReason() : "Bank transfer rejected";
        return ResponseEntity.ok(payoutService.reversePayout(reference, reason));
    }

    private String computeRequestHash(String endpoint, Object requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = endpoint + ":" + (requestBody != null ? requestBody.toString() : "");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "hash-unavailable";
        }
    }
}

