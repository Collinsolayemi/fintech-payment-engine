package com.fintech.engine.service;

import com.fintech.engine.dto.ReconciliationRunResponse;
import com.fintech.engine.dto.SettlementReconciliationRequest;
import com.fintech.engine.model.enums.WebhookProvider;

import java.util.List;
import java.util.UUID;

public interface ReconciliationService {

    /**
     * Runs the internal books integrity suite and persists the run report:
     * - Global trial balance (Σ debits == Σ credits)
     * - Per-currency trial balance
     * - Per-transaction balance
     * - No negative customer wallet balances
     * - PENDING_SETTLEMENTS escrow == Σ in-flight payout principal
     * - Every payout has the journal postings its lifecycle state requires
     * - Every PROCESSED deposit webhook has a ledger posting
     *
     * Never throws for technical failures; the run is persisted with status ERROR instead.
     */
    ReconciliationRunResponse runInternalReconciliation(String triggeredBy);

    /**
     * Matches a provider settlement report against ledger deposit postings for that provider and detects
     * missing-in-ledger, ghost (ledger-only), amount, currency and duplicate-record discrepancies.
     */
    ReconciliationRunResponse runSettlementReconciliation(WebhookProvider provider,
                                                          SettlementReconciliationRequest request,
                                                          String triggeredBy);

    /** Returns a full run report including all discrepancies. */
    ReconciliationRunResponse getRun(UUID runId);

    /** Returns run summaries (without discrepancies), newest first. */
    List<ReconciliationRunResponse> listRuns(int page, int size);
}

