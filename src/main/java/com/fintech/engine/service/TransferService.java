package com.fintech.engine.service;

import com.fintech.engine.dto.TransferRequest;
import com.fintech.engine.dto.TransferResponse;

public interface TransferService {

    /**
     * Executes an atomic, race-condition safe Peer-to-Peer (P2P) transfer.
     * Enforces:
     * - Sender ownership verification (authenticated user must own the sender account)
     * - Distinct sender and recipient
     * - Currency matching
     * - Deadlock-free pessimistic locking
     * - Balance sufficiency check against derived ledger balance
     * - Atomic double-entry journal postings (including optional platform fees)
     */
    TransferResponse transfer(TransferRequest request, String idempotencyKey, String authenticatedUserEmail);

    /**
     * Retrieves transfer receipt by transaction reference.
     */
    TransferResponse getTransferByReference(String reference);
}
