package com.fintech.engine.repository;

import com.fintech.engine.model.entity.VirtualCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VirtualCardRepository extends JpaRepository<VirtualCard, UUID> {
    List<VirtualCard> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<VirtualCard> findByAccountId(UUID accountId);
    Optional<VirtualCard> findByIdAndUserId(UUID id, UUID userId);
    Optional<VirtualCard> findByCardNumber(String cardNumber);
}

