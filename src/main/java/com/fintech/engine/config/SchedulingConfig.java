package com.fintech.engine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's task scheduler for background jobs (e.g. hourly reconciliation).
 * Disable with {@code fintech.reconciliation.scheduler.enabled=false} (used in tests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "fintech.reconciliation.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

