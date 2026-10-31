package com.fintech.engine.service;

import com.fintech.engine.dto.AccountBalanceResponse;
import com.fintech.engine.dto.LedgerPostingRequest;
import com.fintech.engine.dto.TransactionResponse;

import java.util.UUID;

public interface LedgerService {

    /**
     * Executes an atomic, balanced double-entry financial posting.
     * Guarantees:
     * - Sum(Debits) == Sum(Credits)
     * - All amounts > 0
     * - Ordered row locks on accounts to prevent deadlocks and race conditions
     * - Strictly append-only ledger entries
     */
    TransactionResponse postTransaction(LedgerPostingRequest request);

    /**
     * Derives real-time balance for an account from immutable ledger postings.
     */
    AccountBalanceResponse getAccountBalance(UUID accountId);

    /**
     * Global integrity audit verifying that total debits match total credits across the platform.
     */
    boolean verifyGlobalIntegrity();
}
