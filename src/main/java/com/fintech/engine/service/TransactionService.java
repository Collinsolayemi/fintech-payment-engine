package com.fintech.engine.service;

import com.fintech.engine.dto.DepositRequest;
import com.fintech.engine.dto.TransactionResponse;
import com.fintech.engine.dto.TransactionSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionService {

    List<TransactionSummaryResponse> getUserTransactions(UUID userId);

    Page<TransactionSummaryResponse> getUserTransactions(UUID userId, Pageable pageable);

    Page<TransactionSummaryResponse> getAccountTransactions(UUID accountId, Pageable pageable);

    TransactionResponse getTransactionByReference(String reference);

    byte[] exportAccountStatementCsv(UUID accountId, OffsetDateTime from, OffsetDateTime to);

    TransactionResponse deposit(DepositRequest request, String idempotencyKey, String authenticatedEmail);
}

