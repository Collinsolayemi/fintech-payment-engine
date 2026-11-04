package com.fintech.engine.dto;

import com.fintech.engine.model.enums.CardBrand;
import com.fintech.engine.model.enums.CardStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VirtualCardResponse(
        UUID id,
        UUID accountId,
        String accountNumber,
        String currency,
        String cardholderName,
        String maskedCardNumber,
        String lastFour,
        int expiryMonth,
        int expiryYear,
        CardStatus status,
        CardBrand cardBrand,
        String cardType,
        BigDecimal monthlyLimit,
        boolean onlinePaymentsEnabled,
        boolean internationalEnabled,
        OffsetDateTime createdAt
) {}

