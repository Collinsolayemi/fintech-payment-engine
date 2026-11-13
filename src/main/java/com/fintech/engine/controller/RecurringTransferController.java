package com.fintech.engine.controller;

import com.fintech.engine.dto.CreateRecurringTransferRequest;
import com.fintech.engine.dto.RecurringTransferResponse;
import com.fintech.engine.service.RecurringTransferService;
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
@RequestMapping("/api/v1/recurring-transfers")
@Tag(name = "Recurring Transfers", description = "Schedule and manage automated standing orders")
public class RecurringTransferController {

    private final RecurringTransferService recurringTransferService;

    public RecurringTransferController(RecurringTransferService recurringTransferService) {
        this.recurringTransferService = recurringTransferService;
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's standing orders")
    public List<RecurringTransferResponse> list(Authentication authentication) {
        return recurringTransferService.list(authentication.getName());
    }

    @PostMapping
    @Operation(summary = "Create a new scheduled/recurring transfer")
    public ResponseEntity<RecurringTransferResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateRecurringTransferRequest request
    ) {
        RecurringTransferResponse created = recurringTransferService.create(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get standing order details")
    public RecurringTransferResponse getById(Authentication authentication, @PathVariable UUID id) {
        return recurringTransferService.getById(authentication.getName(), id);
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause an active standing order")
    public RecurringTransferResponse pause(Authentication authentication, @PathVariable UUID id) {
        return recurringTransferService.pause(authentication.getName(), id);
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a paused standing order")
    public RecurringTransferResponse resume(Authentication authentication, @PathVariable UUID id) {
        return recurringTransferService.resume(authentication.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a standing order")
    public RecurringTransferResponse cancel(Authentication authentication, @PathVariable UUID id) {
        return recurringTransferService.cancel(authentication.getName(), id);
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "Execute an active standing order immediately")
    public RecurringTransferResponse executeNow(Authentication authentication, @PathVariable UUID id) {
        return recurringTransferService.executeNow(authentication.getName(), id);
    }
}

