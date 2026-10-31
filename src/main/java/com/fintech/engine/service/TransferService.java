package com.fintech.engine.service;

import com.fintech.engine.dto.TransferRequest;
import com.fintech.engine.dto.TransferResponse;

import java.util.UUID;

public interface TransferService {

    /**
     * Executes an atomic, race-condition safe Peer-to-Peer (P2P) transfer.
     * Enforces:
     * - Distinct sender and recipient
     * - Currency matching
     * - Deadlock-free pessimistic locking
     * - Balance sufficiency check against derived ledger balance
     * - Atomic double-entry journal postings (including optional platform fees)
     */
    TransferResponse transfer(TransferRequest request, String idempotencyKey);

    /**
     * Retrieves transfer receipt by transaction reference.
     */
    TransferResponse getTransferByReference(String reference);
}

