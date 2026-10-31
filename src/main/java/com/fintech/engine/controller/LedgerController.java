package com.fintech.engine.controller;

import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/postings")
    public ResponseEntity<TransactionResponse> postTransaction(@Valid @RequestBody LedgerPostingRequest request) {
        TransactionResponse response = ledgerService.postTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/integrity-check")
    public ResponseEntity<Map<String, Object>> verifyGlobalIntegrity() {
        boolean isBalanced = ledgerService.verifyGlobalIntegrity();
        return ResponseEntity.ok(Map.of(
                "healthy", isBalanced,
                "message", isBalanced ? "All debits and credits are balanced across the platform" : "UNBALANCED ENTRIES DETECTED"
        ));
    }
}

