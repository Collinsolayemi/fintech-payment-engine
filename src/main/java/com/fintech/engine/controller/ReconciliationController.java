package com.fintech.engine.controller;

import com.fintech.engine.dto.ReconciliationRunResponse;
import com.fintech.engine.dto.SettlementReconciliationRequest;
import com.fintech.engine.model.enums.WebhookProvider;
import com.fintech.engine.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Reconciliation", description = "Admin-only endpoints for ledger integrity audits and gateway settlement reconciliation")
@RestController
@RequestMapping("/api/v1/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Operation(summary = "Run Internal Integrity Reconciliation",
            description = "Runs the full internal books audit on demand (trial balances, per-transaction balance, negative wallets, escrow vs in-flight payouts, payout & deposit posting consistency) and persists the report.")
    @ApiResponse(responseCode = "201", description = "Run executed; inspect status (PASSED / FAILED / ERROR) and discrepancies")
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator")
    @PostMapping("/runs")
    public ResponseEntity<ReconciliationRunResponse> runInternalReconciliation(Authentication authentication) {
        ReconciliationRunResponse response = reconciliationService.runInternalReconciliation(principal(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Reconcile Gateway Settlement Report",
            description = "Matches a provider settlement report against ledger deposit postings for the period, detecting missing, ghost, amount, currency and duplicate discrepancies.")
    @ApiResponse(responseCode = "201", description = "Run executed; inspect status and discrepancies")
    @ApiResponse(responseCode = "400", description = "Invalid period or malformed settlement records")
    @ApiResponse(responseCode = "403", description = "Caller is not an administrator")
    @PostMapping("/settlements/{provider}")
    public ResponseEntity<ReconciliationRunResponse> runSettlementReconciliation(
            @Parameter(description = "Provider name: STRIPE, PAYSTACK, FLUTTERWAVE") @PathVariable WebhookProvider provider,
            @Valid @RequestBody SettlementReconciliationRequest request,
            Authentication authentication) {
        ReconciliationRunResponse response =
                reconciliationService.runSettlementReconciliation(provider, request, principal(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List Reconciliation Runs", description = "Returns run summaries (without discrepancies), newest first.")
    @ApiResponse(responseCode = "200", description = "Run summaries")
    @GetMapping("/runs")
    public ResponseEntity<List<ReconciliationRunResponse>> listRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reconciliationService.listRuns(page, size));
    }

    @Operation(summary = "Get Reconciliation Run Report", description = "Returns a full run report including every discrepancy.")
    @ApiResponse(responseCode = "200", description = "Run report")
    @ApiResponse(responseCode = "404", description = "Run not found")
    @GetMapping("/runs/{runId}")
    public ResponseEntity<ReconciliationRunResponse> getRun(@PathVariable UUID runId) {
        return ResponseEntity.ok(reconciliationService.getRun(runId));
    }

    private static String principal(Authentication authentication) {
        return authentication != null ? authentication.getName() : "UNKNOWN";
    }
}

