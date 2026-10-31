package com.fintech.engine.controller;

import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Double-Entry Ledger", description = "Endpoints for posting atomic, balanced journal entries and platform integrity auditing")
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Operation(summary = "Post Balanced Journal Entries", description = "Executes an immutable double-entry posting. Requires sum(Debits) == sum(Credits).")
    @ApiResponse(responseCode = "201", description = "Posting executed and ledger entries persisted")
    @ApiResponse(responseCode = "400", description = "Unbalanced ledger violation or invalid monetary amount")
    @PostMapping("/postings")
    public ResponseEntity<TransactionResponse> postTransaction(@Valid @RequestBody LedgerPostingRequest request) {
        TransactionResponse response = ledgerService.postTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Verify Global Ledger Integrity", description = "Validates that platform total debits equal total credits across all accounts")
    @ApiResponse(responseCode = "200", description = "Integrity check result")
    @GetMapping("/integrity-check")
    public ResponseEntity<Map<String, Object>> verifyGlobalIntegrity() {
        boolean isBalanced = ledgerService.verifyGlobalIntegrity();
        return ResponseEntity.ok(Map.of(
                "healthy", isBalanced,
                "message", isBalanced ? "All debits and credits are balanced across the platform" : "UNBALANCED ENTRIES DETECTED"
        ));
    }
}
