package com.fintech.engine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fintech.engine.model.enums.ReconciliationStatus;
import com.fintech.engine.model.enums.ReconciliationType;
import com.fintech.engine.model.enums.WebhookProvider;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reconciliation run report. {@code discrepancies} is omitted in list (summary) views.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReconciliationRunResponse(
    UUID id,
    ReconciliationType type,
    ReconciliationStatus status,
    WebhookProvider provider,
    String triggeredBy,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    BigDecimal totalDebits,
    BigDecimal totalCredits,
    BigDecimal externalTotal,
    BigDecimal ledgerTotal,
    Integer recordsChecked,
    int discrepancyCount,
    String errorMessage,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    List<ReconciliationDiscrepancyResponse> discrepancies
) {}

