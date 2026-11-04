package com.fintech.engine.dto;

import com.fintech.engine.model.enums.CardBrand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record IssueCardRequest(
        @NotNull
        UUID accountId,

        @NotBlank
        String cardholderName,

        CardBrand cardBrand,

        @DecimalMin("100.00")
        BigDecimal monthlyLimit
) {}

