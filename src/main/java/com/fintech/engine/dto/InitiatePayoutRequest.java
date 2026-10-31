package com.fintech.engine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record InitiatePayoutRequest(
    @NotNull(message = "Source accountId is required")
    UUID accountId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum payout amount is 1.00")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    String currency,

    @NotBlank(message = "Destination bank code is required")
    String destinationBankCode,

    @NotBlank(message = "Destination account number is required")
    String destinationAccountNumber,

    @NotBlank(message = "Destination account name is required")
    String destinationAccountName,

    String description
) {}

