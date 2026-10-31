package com.fintech.engine.dto;

import com.fintech.engine.model.enums.WebhookStatus;

public record WebhookResponse(
    boolean success,
    String message,
    String eventId,
    WebhookStatus status
) {}

