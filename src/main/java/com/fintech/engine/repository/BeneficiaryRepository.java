package com.fintech.engine.repository;
import com.fintech.engine.model.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
    List<Beneficiary> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Beneficiary> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndDestinationKey(UUID userId, String destinationKey);
}
