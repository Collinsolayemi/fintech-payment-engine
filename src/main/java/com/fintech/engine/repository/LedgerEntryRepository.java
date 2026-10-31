package com.fintech.engine.repository;

import com.fintech.engine.model.entity.LedgerEntry;
import com.fintech.engine.model.enums.EntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.account.id = :accountId AND le.entryType = :entryType")
    BigDecimal sumAmountByAccountIdAndEntryType(@Param("accountId") UUID accountId, @Param("entryType") EntryType entryType);

    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.entryType = :entryType")
    BigDecimal sumTotalByEntryType(@Param("entryType") EntryType entryType);
}
