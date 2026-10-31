package com.fintech.engine.controller;

import com.fintech.engine.dto.AccountBalanceResponse;
import com.fintech.engine.dto.CreateAccountRequest;
import com.fintech.engine.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountBalanceResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountBalanceResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountBalanceResponse> getAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountBalanceResponse>> getUserAccounts(@PathVariable UUID userId) {
        return ResponseEntity.ok(accountService.getUserAccounts(userId));
    }
}
