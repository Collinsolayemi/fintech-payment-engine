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

    @Query("SELECT le FROM LedgerEntry le JOIN FETCH le.transaction t JOIN FETCH le.account a WHERE a.user.id = :userId ORDER BY le.createdAt DESC")
    List<LedgerEntry> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT le FROM LedgerEntry le JOIN FETCH le.transaction t JOIN FETCH le.account a WHERE a.user.id = :userId ORDER BY le.createdAt DESC")
    Page<LedgerEntry> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT le FROM LedgerEntry le JOIN FETCH le.transaction t JOIN FETCH le.account a WHERE a.id = :accountId AND le.createdAt >= :from AND le.createdAt <= :to ORDER BY le.createdAt ASC")
    List<LedgerEntry> findByAccountIdAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("from") java.time.OffsetDateTime from,
            @Param("to") java.time.OffsetDateTime to);

    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.account.id = :accountId AND le.entryType = :entryType")
    BigDecimal sumAmountByAccountIdAndEntryType(@Param("accountId") UUID accountId, @Param("entryType") EntryType entryType);

    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.entryType = :entryType")
    BigDecimal sumTotalByEntryType(@Param("entryType") EntryType entryType);

    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.account.user.id = :userId AND le.entryType = :entryType AND le.createdAt >= :since")
    BigDecimal sumUserAmountByEntryTypeSince(
            @Param("userId") UUID userId,
            @Param("entryType") EntryType entryType,
            @Param("since") java.time.OffsetDateTime since);
}

