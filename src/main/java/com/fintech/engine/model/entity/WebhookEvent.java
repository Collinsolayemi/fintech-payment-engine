package com.fintech.engine.model.entity;

import com.fintech.engine.model.enums.WebhookProvider;
import com.fintech.engine.model.enums.WebhookStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_events", uniqueConstraints = {
    @UniqueConstraint(name = "uq_webhook_provider_event_id", columnNames = {"provider", "event_id"})
})
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private WebhookProvider provider;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookStatus status = WebhookStatus.RECEIVED;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public WebhookEvent() {}

    public WebhookEvent(WebhookProvider provider, String eventId, String eventType, String payload) {
        this.provider = provider;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = WebhookStatus.RECEIVED;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public void markProcessed() {
        this.status = WebhookStatus.PROCESSED;
        this.processedAt = OffsetDateTime.now();
    }

    public void markFailed() {
        this.status = WebhookStatus.FAILED;
        this.processedAt = OffsetDateTime.now();
    }

    public void markDuplicate() {
        this.status = WebhookStatus.DUPLICATE;
        this.processedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public WebhookProvider getProvider() { return provider; }
    public void setProvider(WebhookProvider provider) { this.provider = provider; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public WebhookStatus getStatus() { return status; }
    public void setStatus(WebhookStatus status) { this.status = status; }

    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}

