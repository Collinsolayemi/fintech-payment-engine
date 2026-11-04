package com.fintech.engine.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ExecuteFxExchangeRequest(
        @NotNull
        UUID quoteId,

        @NotNull
        UUID sourceAccountId,

        @NotNull
        UUID targetAccountId
) {}

