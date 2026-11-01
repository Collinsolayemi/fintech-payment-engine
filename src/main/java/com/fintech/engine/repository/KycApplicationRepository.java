package com.fintech.engine.repository;

import com.fintech.engine.model.entity.KycApplication;
import com.fintech.engine.model.enums.KycApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KycApplicationRepository extends JpaRepository<KycApplication, UUID> {
    List<KycApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByUserIdAndStatus(UUID userId, KycApplicationStatus status);
    Page<KycApplication> findByStatusOrderByCreatedAtAsc(KycApplicationStatus status, Pageable pageable);
    Page<KycApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

