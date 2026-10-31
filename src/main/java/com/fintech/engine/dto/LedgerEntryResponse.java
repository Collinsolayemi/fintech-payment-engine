package com.fintech.engine.dto;

import com.fintech.engine.model.enums.EntryType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
    Long id,
    UUID accountId,
    String accountNumber,
    EntryType entryType,
    BigDecimal amount,
    OffsetDateTime createdAt
) {}
