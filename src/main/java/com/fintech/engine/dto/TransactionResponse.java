package com.fintech.engine.dto;

import com.fintech.engine.model.enums.TransactionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    String reference,
    String idempotencyKey,
    TransactionStatus status,
    String description,
    OffsetDateTime createdAt,
    OffsetDateTime completedAt,
    List<LedgerEntryResponse> entries
) {}
