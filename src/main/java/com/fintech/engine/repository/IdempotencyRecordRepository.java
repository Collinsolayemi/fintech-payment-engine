package com.fintech.engine.repository;

import com.fintech.engine.model.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByKeyAndExpiresAtAfter(String key, OffsetDateTime now);
}
