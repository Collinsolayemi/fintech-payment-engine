package com.fintech.engine.model.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(length = 128)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 32)
    private String status; // IN_FLIGHT, COMPLETED, FAILED

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    public IdempotencyRecord() {}

    public IdempotencyRecord(String key, String requestHash, String status, OffsetDateTime expiresAt) {
        this.key = key;
        this.requestHash = requestHash;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public void markCompleted(int responseCode, String responseBody) {
        this.status = "COMPLETED";
        this.responseCode = responseCode;
        this.responseBody = responseBody;
    }

    public void markFailed(int responseCode, String responseBody) {
        this.status = "FAILED";
        this.responseCode = responseCode;
        this.responseBody = responseBody;
    }

    // Getters and Setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer responseCode) { this.responseCode = responseCode; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
}
