package com.fintech.engine.controller;

import com.fintech.engine.dto.*;
import com.fintech.engine.service.VirtualCardService;
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
import java.util.UUID;

@Tag(name = "Virtual Cards", description = "Endpoints for issuing and managing virtual debit cards linked to multi-currency wallets")
@RestController
@RequestMapping("/api/v1/cards")
public class VirtualCardController {

    private final VirtualCardService virtualCardService;

    public VirtualCardController(VirtualCardService virtualCardService) {
        this.virtualCardService = virtualCardService;
    }

    @Operation(summary = "Issue Virtual Card", description = "Provisions a virtual Visa or Mastercard debit card linked to an active wallet")
    @ApiResponse(responseCode = "201", description = "Card issued successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request or account inactive")
    @PostMapping("/issue")
    public ResponseEntity<VirtualCardResponse> issueCard(
            @Valid @RequestBody IssueCardRequest request,
            Authentication authentication) {
        String userEmail = extractEmail(authentication);
        VirtualCardResponse response = virtualCardService.issueCard(userEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get User Cards", description = "Retrieves all virtual cards belonging to the authenticated user with masked numbers")
    @ApiResponse(responseCode = "200", description = "Cards retrieved successfully")
    @GetMapping("/me")
    public ResponseEntity<List<VirtualCardResponse>> getUserCards(Authentication authentication) {
        String userEmail = extractEmail(authentication);
        return ResponseEntity.ok(virtualCardService.getUserCards(userEmail));
    }

    @Operation(summary = "Reveal Card Details", description = "Securely retrieves the full 16-digit card number and CVV for online checkouts")
    @ApiResponse(responseCode = "200", description = "Sensitive card details returned")
    @ApiResponse(responseCode = "404", description = "Card not found")
    @GetMapping("/{cardId}/details")
    public ResponseEntity<CardDetailsResponse> getCardDetails(
            @Parameter(description = "Card UUID") @PathVariable UUID cardId,
            Authentication authentication) {
        String userEmail = extractEmail(authentication);
        return ResponseEntity.ok(virtualCardService.getCardDetails(userEmail, cardId));
    }

    @Operation(summary = "Update Card Status", description = "Freezes or unfreezes a virtual card instantly")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "404", description = "Card not found")
    @PatchMapping("/{cardId}/status")
    public ResponseEntity<VirtualCardResponse> updateStatus(
            @Parameter(description = "Card UUID") @PathVariable UUID cardId,
            @Valid @RequestBody UpdateCardStatusRequest request,
            Authentication authentication) {
        String userEmail = extractEmail(authentication);
        return ResponseEntity.ok(virtualCardService.updateCardStatus(userEmail, cardId, request));
    }

    @Operation(summary = "Update Card Controls & Limits", description = "Configures monthly spend limit and toggles for online and international payments")
    @ApiResponse(responseCode = "200", description = "Controls updated")
    @ApiResponse(responseCode = "404", description = "Card not found")
    @PatchMapping("/{cardId}/controls")
    public ResponseEntity<VirtualCardResponse> updateControls(
            @Parameter(description = "Card UUID") @PathVariable UUID cardId,
            @RequestBody UpdateCardControlsRequest request,
            Authentication authentication) {
        String userEmail = extractEmail(authentication);
        return ResponseEntity.ok(virtualCardService.updateCardControls(userEmail, cardId, request));
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

