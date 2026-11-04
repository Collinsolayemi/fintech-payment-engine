package com.fintech.engine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record FxQuoteRequest(
        @NotBlank @Size(min = 3, max = 3)
        String sourceCurrency,

        @NotBlank @Size(min = 3, max = 3)
        String targetCurrency,

        @NotNull @DecimalMin("0.50")
        BigDecimal sourceAmount
) {}

