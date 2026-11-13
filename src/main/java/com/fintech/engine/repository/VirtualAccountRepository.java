package com.fintech.engine.repository;

import com.fintech.engine.model.entity.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    List<VirtualAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<VirtualAccount> findByUserIdAndCurrency(UUID userId, String currency);

    Optional<VirtualAccount> findByAccountNumber(String accountNumber);

    Optional<VirtualAccount> findByIban(String iban);

    boolean existsByUserIdAndCurrency(UUID userId, String currency);

    boolean existsByAccountNumber(String accountNumber);
}
