package com.fintech.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LedgerPostingRequest(
    @NotBlank(message = "Transaction reference is required")
    String reference,

    String idempotencyKey,

    String description,

    @NotEmpty(message = "Ledger entries list cannot be empty")
    @Size(min = 2, message = "A double-entry posting requires at least 2 entries (one debit, one credit)")
    @Valid
    List<LedgerEntryCommand> entries
) {}
