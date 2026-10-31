package com.fintech.engine.dto;

import com.fintech.engine.model.enums.TransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferResponse(
    String transactionReference,
    String idempotencyKey,
    TransactionStatus status,
    BigDecimal amount,
    BigDecimal fee,
    String currency,
    UUID senderAccountId,
    UUID recipientAccountId,
    String description,
    OffsetDateTime timestamp
) {}

