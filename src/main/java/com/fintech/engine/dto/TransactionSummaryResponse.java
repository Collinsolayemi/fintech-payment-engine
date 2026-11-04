package com.fintech.engine.dto;

import com.fintech.engine.model.enums.EntryType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionSummaryResponse(
    UUID id,
    String reference,
    String description,
    String status,
    BigDecimal amount,
    String currency,
    EntryType entryType,
    UUID accountId,
    String accountNumber,
    OffsetDateTime createdAt
) {}
