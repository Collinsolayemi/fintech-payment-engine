package com.fintech.engine.dto;

import java.util.UUID;

public record CardDetailsResponse(
        UUID id,
        String cardNumber,
        String cvv,
        int expiryMonth,
        int expiryYear,
        String cardholderName
) {}

