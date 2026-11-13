package com.fintech.engine.controller;

import com.fintech.engine.dto.CreateVirtualAccountRequest;
import com.fintech.engine.dto.SimulateVirtualAccountDepositRequest;
import com.fintech.engine.dto.VirtualAccountDepositResponse;
import com.fintech.engine.dto.VirtualAccountResponse;
import com.fintech.engine.model.enums.VirtualAccountStatus;
import com.fintech.engine.service.VirtualAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/virtual-accounts")
@Tag(name = "Virtual Accounts", description = "Dedicated inbound bank account numbers (NUBAN, ACH, Sort Code, IBAN) linked to wallets")
public class VirtualAccountController {

    private final VirtualAccountService virtualAccountService;

    public VirtualAccountController(VirtualAccountService virtualAccountService) {
        this.virtualAccountService = virtualAccountService;
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's virtual accounts")
    public List<VirtualAccountResponse> list(Authentication authentication) {
        return virtualAccountService.getUserVirtualAccounts(authentication.getName());
    }

    @PostMapping
    @Operation(summary = "Provision a new currency virtual bank account")
    public ResponseEntity<VirtualAccountResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateVirtualAccountRequest request
    ) {
        VirtualAccountResponse response = virtualAccountService.createVirtualAccount(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get virtual account details")
    public VirtualAccountResponse getById(Authentication authentication, @PathVariable UUID id) {
        return virtualAccountService.getVirtualAccount(authentication.getName(), id);
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend an active virtual account")
    public VirtualAccountResponse suspend(Authentication authentication, @PathVariable UUID id) {
        return virtualAccountService.updateStatus(authentication.getName(), id, VirtualAccountStatus.SUSPENDED);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Reactivate a suspended virtual account")
    public VirtualAccountResponse activate(Authentication authentication, @PathVariable UUID id) {
        return virtualAccountService.updateStatus(authentication.getName(), id, VirtualAccountStatus.ACTIVE);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Permanently close a virtual account")
    public VirtualAccountResponse close(Authentication authentication, @PathVariable UUID id) {
        return virtualAccountService.updateStatus(authentication.getName(), id, VirtualAccountStatus.CLOSED);
    }

    @PostMapping("/{id}/simulate-deposit")
    @Operation(summary = "Simulate an inbound bank transfer to a virtual account and credit its linked wallet")
    public ResponseEntity<VirtualAccountDepositResponse> simulateDeposit(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SimulateVirtualAccountDepositRequest request
    ) {
        VirtualAccountDepositResponse response = virtualAccountService.simulateInboundDeposit(
                authentication.getName(),
                id,
                request,
                idempotencyKey
        );
        return ResponseEntity.ok(response);
    }
}

