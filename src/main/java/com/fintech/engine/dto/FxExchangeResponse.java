package com.fintech.engine.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FxExchangeResponse(
        String transactionReference,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal sourceAmount,
        BigDecimal targetAmount,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal exchangeRate,
        BigDecimal fee,
        OffsetDateTime executedAt
) {}

