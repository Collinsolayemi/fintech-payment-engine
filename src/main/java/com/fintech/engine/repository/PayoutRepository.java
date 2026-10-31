package com.fintech.engine.repository;

import com.fintech.engine.model.entity.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    Optional<Payout> findByReference(String reference);
    List<Payout> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Payout> findByIdempotencyKey(String idempotencyKey);
}

