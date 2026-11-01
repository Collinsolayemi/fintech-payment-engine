package com.fintech.engine.model.entity;

import com.fintech.engine.model.enums.DiscrepancySeverity;
import com.fintech.engine.model.enums.DiscrepancyType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single finding produced by a reconciliation run. Append-only audit record.
 */
@Entity
@Table(name = "reconciliation_discrepancies")
public class ReconciliationDiscrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, updatable = false, length = 64)
    private DiscrepancyType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private DiscrepancySeverity severity;

    @Column(length = 128, updatable = false)
    private String reference;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    @Column(length = 3, updatable = false)
    private String currency;

    @Column(name = "expected_amount", precision = 19, scale = 4, updatable = false)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", precision = 19, scale = 4, updatable = false)
    private BigDecimal actualAmount;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public ReconciliationDiscrepancy() {}

    public ReconciliationDiscrepancy(UUID runId, DiscrepancyType type, String reference, UUID accountId,
                                     String currency, BigDecimal expectedAmount, BigDecimal actualAmount,
                                     String details) {
        this.runId = runId;
        this.type = type;
        this.severity = type.getDefaultSeverity();
        this.reference = reference;
        this.accountId = accountId;
        this.currency = currency;
        this.expectedAmount = expectedAmount;
        this.actualAmount = actualAmount;
        this.details = details;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    // Getters only (Immutability by design)
    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public DiscrepancyType getType() { return type; }
    public DiscrepancySeverity getSeverity() { return severity; }
    public String getReference() { return reference; }
    public UUID getAccountId() { return accountId; }
    public String getCurrency() { return currency; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public BigDecimal getActualAmount() { return actualAmount; }
    public String getDetails() { return details; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

