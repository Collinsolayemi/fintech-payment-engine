package com.fintech.engine.scheduler;

import com.fintech.engine.dto.ReconciliationRunResponse;
import com.fintech.engine.model.enums.ReconciliationStatus;
import com.fintech.engine.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically runs the internal integrity reconciliation (hourly by default, per docs/ARCHITECTURE.md §7).
 *
 * NOTE: when running multiple application instances, every instance will execute this job.
 * Results are read-only and idempotent, but add a distributed lock (e.g. ShedLock / Redisson)
 * before scaling out to avoid redundant audit runs.
 */
@Component
@ConditionalOnProperty(prefix = "fintech.reconciliation.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduler {

    static final String SYSTEM_TRIGGER = "SYSTEM_SCHEDULER";

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(cron = "${fintech.reconciliation.scheduler.internal-cron:0 0 * * * *}", zone = "UTC")
    public void runScheduledIntegrityCheck() {
        try {
            ReconciliationRunResponse result = reconciliationService.runInternalReconciliation(SYSTEM_TRIGGER);
            if (result.status() != ReconciliationStatus.PASSED) {
                // Hook point for paging / alerting integrations
                log.error("ALERT: scheduled reconciliation run {} finished with status {} ({} discrepancies)",
                        result.id(), result.status(), result.discrepancyCount());
            }
        } catch (Exception ex) {
            log.error("ALERT: scheduled reconciliation could not be executed", ex);
        }
    }
}

