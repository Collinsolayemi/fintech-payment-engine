package com.fintech.engine.repository;

import com.fintech.engine.model.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {

    List<PaymentRequest> findByRequesterUserIdOrderByCreatedAtDesc(UUID requesterUserId);

    List<PaymentRequest> findByPayerUserIdOrderByCreatedAtDesc(UUID payerUserId);

    Optional<PaymentRequest> findByRequestCode(String requestCode);

    boolean existsByRequestCode(String requestCode);
}

