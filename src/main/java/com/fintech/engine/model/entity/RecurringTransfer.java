package com.fintech.engine.model.entity;

import com.fintech.engine.model.enums.RecurrenceFrequency;
import com.fintech.engine.model.enums.RecurringTransferStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recurring_transfers")
public class RecurringTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_account_id", nullable = false)
    private Account senderAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_account_id", nullable = false)
    private Account recipientAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurrenceFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurringTransferStatus status = RecurringTransferStatus.ACTIVE;

    @Column(name = "next_run_at", nullable = false)
    private OffsetDateTime nextRunAt;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "last_transaction_reference", length = 100)
    private String lastTransactionReference;

    @Column(name = "last_failure_reason", length = 500)
    private String lastFailureReason;

    @Column(name = "execution_count", nullable = false)
    private int executionCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public RecurringTransfer() {
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Account getSenderAccount() { return senderAccount; }
    public void setSenderAccount(Account senderAccount) { this.senderAccount = senderAccount; }

    public Account getRecipientAccount() { return recipientAccount; }
    public void setRecipientAccount(Account recipientAccount) { this.recipientAccount = recipientAccount; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public RecurrenceFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurrenceFrequency frequency) { this.frequency = frequency; }

    public RecurringTransferStatus getStatus() { return status; }
    public void setStatus(RecurringTransferStatus status) { this.status = status; }

    public OffsetDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(OffsetDateTime nextRunAt) { this.nextRunAt = nextRunAt; }

    public OffsetDateTime getEndDate() { return endDate; }
    public void setEndDate(OffsetDateTime endDate) { this.endDate = endDate; }

    public OffsetDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(OffsetDateTime lastRunAt) { this.lastRunAt = lastRunAt; }

    public String getLastTransactionReference() { return lastTransactionReference; }
    public void setLastTransactionReference(String lastTransactionReference) { this.lastTransactionReference = lastTransactionReference; }

    public String getLastFailureReason() { return lastFailureReason; }
    public void setLastFailureReason(String lastFailureReason) { this.lastFailureReason = lastFailureReason; }

    public int getExecutionCount() { return executionCount; }
    public void setExecutionCount(int executionCount) { this.executionCount = executionCount; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

