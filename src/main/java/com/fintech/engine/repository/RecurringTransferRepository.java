package com.fintech.engine.repository;

import com.fintech.engine.model.entity.RecurringTransfer;
import com.fintech.engine.model.enums.RecurringTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringTransferRepository extends JpaRepository<RecurringTransfer, UUID> {

    List<RecurringTransfer> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<RecurringTransfer> findByStatusAndNextRunAtLessThanEqual(RecurringTransferStatus status, OffsetDateTime cutoff);
}

