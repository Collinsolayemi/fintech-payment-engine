package com.fintech.engine.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PayPaymentRequestDto(
        @NotNull(message = "Payer source wallet account ID is required")
        UUID payerAccountId
) {
}

