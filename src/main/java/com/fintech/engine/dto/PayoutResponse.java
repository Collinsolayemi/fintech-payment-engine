package com.fintech.engine.dto;

import com.fintech.engine.model.enums.PayoutStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PayoutResponse(
    UUID id,
    String reference,
    UUID accountId,
    String accountNumber,
    BigDecimal amount,
    BigDecimal fee,
    String currency,
    PayoutStatus status,
    String destinationBankCode,
    String destinationAccountNumber,
    String destinationAccountName,
    String failureReason,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}

