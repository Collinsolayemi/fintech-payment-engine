package com.fintech.engine.controller;

import com.fintech.engine.dto.AccountBalanceResponse;
import com.fintech.engine.dto.CreateAccountRequest;
import com.fintech.engine.dto.CreateCurrencyWalletRequest;
import com.fintech.engine.service.AccountService;
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

@Tag(name = "Accounts & Wallets", description = "Endpoints for managing user wallets, system accounts, and balance derivations")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Create an Account", description = "Creates a new user wallet or internal chart of accounts container")
    @ApiResponse(responseCode = "201", description = "Account created successfully")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @PostMapping
    public ResponseEntity<AccountBalanceResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountBalanceResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Create Multi-Currency Wallet", description = "Provisions a new currency wallet (e.g. EUR, GBP, CAD) for the authenticated user")
    @ApiResponse(responseCode = "201", description = "Wallet provisioned successfully")
    @ApiResponse(responseCode = "400", description = "Invalid currency code")
    @ApiResponse(responseCode = "409", description = "User already has a wallet in this currency")
    @PostMapping("/wallet")
    public ResponseEntity<AccountBalanceResponse> createCurrencyWallet(
            @Valid @RequestBody CreateCurrencyWalletRequest request,
            Authentication authentication) {
        String email = extractEmail(authentication);
        AccountBalanceResponse response = accountService.createCurrencyWallet(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get Account Balance", description = "Retrieves real-time derived balance, debit, and credit summaries from the immutable ledger")
    @ApiResponse(responseCode = "200", description = "Account balance retrieved")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountBalanceResponse> getAccount(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @Operation(summary = "Get User Accounts", description = "Retrieves all wallets and accounts associated with a specific user")
    @ApiResponse(responseCode = "200", description = "User accounts retrieved")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountBalanceResponse>> getUserAccounts(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        return ResponseEntity.ok(accountService.getUserAccounts(userId));
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
