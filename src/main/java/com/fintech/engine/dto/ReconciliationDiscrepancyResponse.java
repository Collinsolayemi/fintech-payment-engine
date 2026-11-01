package com.fintech.engine.dto;

import com.fintech.engine.model.enums.DiscrepancySeverity;
import com.fintech.engine.model.enums.DiscrepancyType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReconciliationDiscrepancyResponse(
    UUID id,
    DiscrepancyType type,
    DiscrepancySeverity severity,
    String reference,
    UUID accountId,
    String currency,
    BigDecimal expectedAmount,
    BigDecimal actualAmount,
    String details,
    OffsetDateTime createdAt
) {}

