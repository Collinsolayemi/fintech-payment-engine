package com.fintech.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A provider settlement report covering the half-open window [periodStart, periodEnd).
 * An empty {@code records} list is valid: every ledger deposit in the window is then reported as a ghost.
 */
public record SettlementReconciliationRequest(
    @NotNull(message = "periodStart is required")
    OffsetDateTime periodStart,

    @NotNull(message = "periodEnd is required")
    OffsetDateTime periodEnd,

    @NotNull(message = "records list is required (may be empty)")
    @Size(max = 50000, message = "A single settlement report may contain at most 50000 records")
    @Valid
    List<SettlementRecord> records
) {}

