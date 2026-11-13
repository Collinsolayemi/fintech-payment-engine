package com.fintech.engine.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VirtualAccountDepositResponse(
        String transactionReference,
        UUID virtualAccountId,
        UUID walletAccountId,
        String virtualAccountNumber,
        String currency,
        BigDecimal amount,
        BigDecimal newWalletBalance,
        String senderName,
        String narration,
        OffsetDateTime creditedAt
) {
}

