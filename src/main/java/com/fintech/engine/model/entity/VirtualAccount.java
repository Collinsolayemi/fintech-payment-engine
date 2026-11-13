package com.fintech.engine.model.entity;

import com.fintech.engine.model.enums.VirtualAccountStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "virtual_accounts")
public class VirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "account_number", nullable = false, unique = true, length = 34)
    private String accountNumber;

    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "routing_number", length = 50)
    private String routingNumber;

    @Column(name = "sort_code", length = 20)
    private String sortCode;

    @Column(length = 34)
    private String iban;

    @Column(name = "bic_swift", length = 20)
    private String bicSwift;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VirtualAccountStatus status = VirtualAccountStatus.ACTIVE;

    @Column(nullable = false, length = 50)
    private String provider = "SIMULATED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public VirtualAccount() {
    }

    public VirtualAccount(User user, Account account, String currency, String accountNumber,
                          String accountName, String bankName, String routingNumber,
                          String sortCode, String iban, String bicSwift,
                          VirtualAccountStatus status, String provider) {
        this.user = user;
        this.account = account;
        this.currency = currency;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.bankName = bankName;
        this.routingNumber = routingNumber;
        this.sortCode = sortCode;
        this.iban = iban;
        this.bicSwift = bicSwift;
        this.status = status != null ? status : VirtualAccountStatus.ACTIVE;
        this.provider = provider != null ? provider : "SIMULATED";
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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getRoutingNumber() {
        return routingNumber;
    }

    public void setRoutingNumber(String routingNumber) {
        this.routingNumber = routingNumber;
    }

    public String getSortCode() {
        return sortCode;
    }

    public void setSortCode(String sortCode) {
        this.sortCode = sortCode;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getBicSwift() {
        return bicSwift;
    }

    public void setBicSwift(String bicSwift) {
        this.bicSwift = bicSwift;
    }

    public VirtualAccountStatus getStatus() {
        return status;
    }

    public void setStatus(VirtualAccountStatus status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
