package com.fintech.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCurrencyWalletRequest(
        @NotBlank @Size(min = 3, max = 3)
        String currency,

        String accountName
) {}

