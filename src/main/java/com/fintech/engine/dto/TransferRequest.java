package com.fintech.engine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
    @NotNull(message = "Sender account ID is required")
    UUID senderAccountId,

    @NotNull(message = "Recipient account ID is required")
    UUID recipientAccountId,

    @NotNull(message = "Transfer amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    BigDecimal amount,

    @DecimalMin(value = "0.00", message = "Fee cannot be negative")
    BigDecimal fee,

    String currency,

    String description
) {
    public BigDecimal resolvedFee() {
        return fee != null ? fee : BigDecimal.ZERO;
    }
}

