package com.fintech.engine.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FxQuoteResponse(
        UUID quoteId,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal sourceAmount,
        BigDecimal targetAmount,
        BigDecimal exchangeRate,
        BigDecimal fee,
        long expiresInSeconds,
        OffsetDateTime expiresAt
) {}

