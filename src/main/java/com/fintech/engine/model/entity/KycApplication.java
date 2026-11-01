package com.fintech.engine.model.entity;

import com.fintech.engine.model.enums.IdType;
import com.fintech.engine.model.enums.KycApplicationStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_applications")
public class KycApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "target_tier", nullable = false)
    private int targetTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 64)
    private IdType idType;

    @Column(name = "id_number", nullable = false, length = 128)
    private String idNumber;

    @Column(name = "document_url", length = 512)
    private String documentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KycApplicationStatus status = KycApplicationStatus.SUBMITTED;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public KycApplication() {}

    public KycApplication(User user, int targetTier, IdType idType, String idNumber, String documentUrl) {
        this.user = user;
        this.targetTier = targetTier;
        this.idType = idType;
        this.idNumber = idNumber;
        this.documentUrl = documentUrl;
        this.status = KycApplicationStatus.SUBMITTED;
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

    public void approve(String reviewer) {
        this.status = KycApplicationStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.updatedAt = OffsetDateTime.now();
    }

    public void reject(String reviewer, String reason) {
        this.status = KycApplicationStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.rejectionReason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public int getTargetTier() { return targetTier; }
    public void setTargetTier(int targetTier) { this.targetTier = targetTier; }

    public IdType getIdType() { return idType; }
    public void setIdType(IdType idType) { this.idType = idType; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public KycApplicationStatus getStatus() { return status; }
    public void setStatus(KycApplicationStatus status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

