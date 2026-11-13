package com.fintech.engine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SimulateVirtualAccountDepositRequest(
        @NotNull(message = "Deposit amount is required")
        @DecimalMin(value = "0.01", message = "Deposit amount must be greater than zero")
        BigDecimal amount,

        @Size(max = 120, message = "Sender name must not exceed 120 characters")
        String senderName,

        @Size(max = 255, message = "Narration must not exceed 255 characters")
        String narration
) {
}

