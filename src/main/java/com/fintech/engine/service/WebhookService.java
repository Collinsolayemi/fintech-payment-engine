package com.fintech.engine.service;

import com.fintech.engine.dto.WebhookResponse;
import com.fintech.engine.model.enums.WebhookProvider;

public interface WebhookService {
    WebhookResponse processWebhook(WebhookProvider provider, String signature, String rawPayload);
}

