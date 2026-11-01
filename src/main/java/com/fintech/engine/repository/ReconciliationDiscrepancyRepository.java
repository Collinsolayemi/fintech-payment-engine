package com.fintech.engine.repository;

import com.fintech.engine.model.entity.ReconciliationDiscrepancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReconciliationDiscrepancyRepository extends JpaRepository<ReconciliationDiscrepancy, UUID> {
    List<ReconciliationDiscrepancy> findByRunIdOrderByCreatedAtAsc(UUID runId);
}

