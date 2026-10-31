package com.fintech.engine.dto;

import com.fintech.engine.model.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAccountRequest(
    UUID userId,

    @NotBlank(message = "Account name is required")
    String accountName,

    @NotNull(message = "Account type is required")
    AccountType type,

    String currency
) {}

