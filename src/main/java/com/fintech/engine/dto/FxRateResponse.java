package com.fintech.engine.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FxRateResponse(
        String sourceCurrency,
        String targetCurrency,
        BigDecimal rate,
        BigDecimal inverseRate,
        OffsetDateTime timestamp
) {}

