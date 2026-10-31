package com.fintech.engine.repository;

import com.fintech.engine.model.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByReference(String reference);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    boolean existsByReference(String reference);
}

