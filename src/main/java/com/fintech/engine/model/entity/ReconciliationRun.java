package com.fintech.engine.model.entity;

import com.fintech.engine.model.enums.ReconciliationStatus;
import com.fintech.engine.model.enums.ReconciliationType;
import com.fintech.engine.model.enums.WebhookProvider;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 32)
    private ReconciliationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReconciliationStatus status = ReconciliationStatus.RUNNING;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private WebhookProvider provider;

    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Column(name = "period_start")
    private OffsetDateTime periodStart;

    @Column(name = "period_end")
    private OffsetDateTime periodEnd;

    @Column(name = "total_debits", precision = 19, scale = 4)
    private BigDecimal totalDebits;

    @Column(name = "total_credits", precision = 19, scale = 4)
    private BigDecimal totalCredits;

    @Column(name = "external_total", precision = 19, scale = 4)
    private BigDecimal externalTotal;

    @Column(name = "ledger_total", precision = 19, scale = 4)
    private BigDecimal ledgerTotal;

    @Column(name = "records_checked")
    private Integer recordsChecked;

    @Column(name = "discrepancy_count", nullable = false)
    private int discrepancyCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public ReconciliationRun() {}

    public ReconciliationRun(ReconciliationType type, WebhookProvider provider, String triggeredBy) {
        this.type = type;
        this.provider = provider;
        this.triggeredBy = triggeredBy;
        this.status = ReconciliationStatus.RUNNING;
    }

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = OffsetDateTime.now();
        }
    }

    public void complete(int discrepancyCount) {
        this.discrepancyCount = discrepancyCount;
        this.status = discrepancyCount == 0 ? ReconciliationStatus.PASSED : ReconciliationStatus.FAILED;
        this.completedAt = OffsetDateTime.now();
    }

    public void markError(String errorMessage) {
        this.status = ReconciliationStatus.ERROR;
        this.errorMessage = errorMessage;
        this.completedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ReconciliationType getType() { return type; }
    public ReconciliationStatus getStatus() { return status; }
    public WebhookProvider getProvider() { return provider; }
    public String getTriggeredBy() { return triggeredBy; }

    public OffsetDateTime getPeriodStart() { return periodStart; }
    public void setPeriodStart(OffsetDateTime periodStart) { this.periodStart = periodStart; }

    public OffsetDateTime getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(OffsetDateTime periodEnd) { this.periodEnd = periodEnd; }

    public BigDecimal getTotalDebits() { return totalDebits; }
    public void setTotalDebits(BigDecimal totalDebits) { this.totalDebits = totalDebits; }

    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }

    public BigDecimal getExternalTotal() { return externalTotal; }
    public void setExternalTotal(BigDecimal externalTotal) { this.externalTotal = externalTotal; }

    public BigDecimal getLedgerTotal() { return ledgerTotal; }
    public void setLedgerTotal(BigDecimal ledgerTotal) { this.ledgerTotal = ledgerTotal; }

    public Integer getRecordsChecked() { return recordsChecked; }
    public void setRecordsChecked(Integer recordsChecked) { this.recordsChecked = recordsChecked; }

    public int getDiscrepancyCount() { return discrepancyCount; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
}

