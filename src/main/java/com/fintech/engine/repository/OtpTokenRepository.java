package com.fintech.engine.repository;

import com.fintech.engine.model.entity.OtpToken;
import com.fintech.engine.model.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {

    Optional<OtpToken> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    Optional<OtpToken> findTopByEmailAndPurposeAndVerifiedTrueOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    @Modifying
    @Query("DELETE FROM OtpToken o WHERE o.expiresAt < :now")
    void deleteExpiredOtps(@Param("now") OffsetDateTime now);
}

