package com.fintech.engine.repository;

import com.fintech.engine.model.entity.FxQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FxQuoteRepository extends JpaRepository<FxQuote, UUID> {
    Optional<FxQuote> findByIdAndUserId(UUID id, UUID userId);
    List<FxQuote> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}

