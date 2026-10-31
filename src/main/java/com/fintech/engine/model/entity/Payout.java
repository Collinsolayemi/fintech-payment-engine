package com.fintech.engine.model.entity;

import com.fintech.engine.model.enums.PayoutStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payouts")
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PayoutStatus status = PayoutStatus.PENDING_ESCROW;

    @Column(name = "destination_bank_code", nullable = false, length = 32)
    private String destinationBankCode;

    @Column(name = "destination_account_number", nullable = false, length = 64)
    private String destinationAccountNumber;

    @Column(name = "destination_account_name", nullable = false, length = 128)
    private String destinationAccountName;

    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Payout() {}

    public Payout(String reference, User user, Account account, BigDecimal amount, BigDecimal fee,
                  String currency, String destinationBankCode, String destinationAccountNumber,
                  String destinationAccountName, String idempotencyKey) {
        this.reference = reference;
        this.user = user;
        this.account = account;
        this.amount = amount;
        this.fee = fee != null ? fee : BigDecimal.ZERO;
        this.currency = currency;
        this.destinationBankCode = destinationBankCode;
        this.destinationAccountNumber = destinationAccountNumber;
        this.destinationAccountName = destinationAccountName;
        this.idempotencyKey = idempotencyKey;
        this.status = PayoutStatus.PENDING_ESCROW;
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

    public void markProcessing() {
        this.status = PayoutStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = PayoutStatus.COMPLETED;
    }

    public void markFailed(String reason) {
        this.status = PayoutStatus.FAILED;
        this.failureReason = reason;
    }

    public void markReversed(String reason) {
        this.status = PayoutStatus.REVERSED;
        this.failureReason = reason;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PayoutStatus getStatus() { return status; }
    public void setStatus(PayoutStatus status) { this.status = status; }

    public String getDestinationBankCode() { return destinationBankCode; }
    public void setDestinationBankCode(String destinationBankCode) { this.destinationBankCode = destinationBankCode; }

    public String getDestinationAccountNumber() { return destinationAccountNumber; }
    public void setDestinationAccountNumber(String destinationAccountNumber) { this.destinationAccountNumber = destinationAccountNumber; }

    public String getDestinationAccountName() { return destinationAccountName; }
    public void setDestinationAccountName(String destinationAccountName) { this.destinationAccountName = destinationAccountName; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

