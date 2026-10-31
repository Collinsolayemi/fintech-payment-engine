package com.fintech.engine.repository;

import com.fintech.engine.model.entity.WebhookEvent;
import com.fintech.engine.model.enums.WebhookProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    Optional<WebhookEvent> findByProviderAndEventId(WebhookProvider provider, String eventId);
    boolean existsByProviderAndEventId(WebhookProvider provider, String eventId);
}

