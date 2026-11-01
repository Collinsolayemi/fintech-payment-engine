package com.fintech.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A single line from a payment provider's settlement report.
 *
 * @param reference provider event id; must match the {@code eventId} used when the deposit
 *                  webhook was ingested (ledger reference {@code DEP-{PROVIDER}-{reference}}).
 * @param amount    gross amount the provider settled for this payment.
 * @param currency  ISO-4217 currency code.
 */
public record SettlementRecord(
    @NotBlank(message = "Settlement reference is required")
    @Size(max = 128, message = "Settlement reference must be at most 128 characters")
    String reference,

    @NotNull(message = "Settlement amount is required")
    @Positive(message = "Settlement amount must be greater than zero")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    String currency
) {}
