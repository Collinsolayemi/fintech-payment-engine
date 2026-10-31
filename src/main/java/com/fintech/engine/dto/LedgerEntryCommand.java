package com.fintech.engine.dto;

import com.fintech.engine.model.enums.EntryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryCommand(
    @NotNull(message = "Account ID is required")
    UUID accountId,

    @NotNull(message = "Entry type (DEBIT or CREDIT) is required")
    EntryType entryType,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be greater than zero")
    BigDecimal amount
) {}

