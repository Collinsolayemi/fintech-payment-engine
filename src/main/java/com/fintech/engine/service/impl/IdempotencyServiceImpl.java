package com.fintech.engine.service.impl;

import com.fintech.engine.exception.IdempotencyConflictException;
import com.fintech.engine.model.entity.IdempotencyRecord;
import com.fintech.engine.repository.IdempotencyRecordRepository;
import com.fintech.engine.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyServiceImpl.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyServiceImpl(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotencyRecord> checkOrStart(String key, String requestHash) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findById(key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if ("COMPLETED".equals(record.getStatus())) {
                log.info("Idempotency hit! Returning cached response for key: {}", key);
                return Optional.of(record);
            }
            if ("IN_FLIGHT".equals(record.getStatus())) {
                log.warn("Idempotency conflict! Key {} is currently in-flight", key);
                throw new IdempotencyConflictException(key);
            }
        }

        // New request: record as IN_FLIGHT with 24-hour TTL
        IdempotencyRecord newRecord = new IdempotencyRecord(
                key,
                requestHash,
                "IN_FLIGHT",
                OffsetDateTime.now().plusHours(24)
        );
        idempotencyRecordRepository.save(newRecord);
        return Optional.empty();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, int statusCode, String responseBody) {
        if (key == null || key.isBlank()) return;

        idempotencyRecordRepository.findById(key).ifPresent(record -> {
            record.markCompleted(statusCode, responseBody);
            idempotencyRecordRepository.save(record);
            log.info("Idempotency key marked COMPLETED: {}", key);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String key) {
        if (key == null || key.isBlank()) return;

        idempotencyRecordRepository.findById(key).ifPresent(record -> {
            record.markFailed(500, "{\"error\": \"Transaction execution failed\"}");
            idempotencyRecordRepository.save(record);
            log.warn("Idempotency key marked FAILED: {}", key);
        });
    }
}

