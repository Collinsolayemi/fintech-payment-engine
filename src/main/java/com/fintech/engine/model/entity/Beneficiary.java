package com.fintech.engine.model.entity;
import com.fintech.engine.model.enums.BeneficiaryType;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.OffsetDateTime;
@Entity
@Table(name = "beneficiaries", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "destination_key"}))
public class Beneficiary {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    @Column(nullable = false, name = "user_id")
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "type")
    private BeneficiaryType type;
    @Column(nullable = false, name = "nickname", length = 100)
    private String nickname;
    @Column(name = "recipient_account_id")
    private UUID recipientAccountId;
    @Column(name = "bank_code", length = 32)
    private String bankCode;
    @Column(name = "account_number", length = 64)
    private String accountNumber;
    @Column(nullable = false, name = "account_name", length = 200)
    private String accountName;
    @Column(nullable = false, name = "currency", length = 3)
    private String currency;
    @Column(nullable = false, name = "destination_key", length = 200)
    private String destinationKey;
    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt;
    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); }
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID value) { userId = value; }
    public BeneficiaryType getType() { return type; }
    public void setType(BeneficiaryType value) { type = value; }
    public String getNickname() { return nickname; }
    public void setNickname(String value) { nickname = value; }
    public UUID getRecipientAccountId() { return recipientAccountId; }
    public void setRecipientAccountId(UUID value) { recipientAccountId = value; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String value) { bankCode = value; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String value) { accountNumber = value; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String value) { accountName = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public String getDestinationKey() { return destinationKey; }
    public void setDestinationKey(String value) { destinationKey = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
}
