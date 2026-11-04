package com.fintech.engine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record DepositRequest(
    @NotNull(message = "Destination account ID is required")
    UUID accountId,

    @NotNull(message = "Deposit amount is required")
    @DecimalMin(value = "0.01", message = "Deposit amount must be at least 0.01")
    BigDecimal amount,

    String currency,

    String description
) {}
