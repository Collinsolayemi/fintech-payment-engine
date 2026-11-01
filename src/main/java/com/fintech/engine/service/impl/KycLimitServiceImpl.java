package com.fintech.engine.service.impl;

import com.fintech.engine.exception.KycLimitExceededException;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.KycTierPolicy;
import com.fintech.engine.repository.LedgerEntryRepository;
import com.fintech.engine.service.KycLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class KycLimitServiceImpl implements KycLimitService {

    private static final Logger log = LoggerFactory.getLogger(KycLimitServiceImpl.class);

    private final LedgerEntryRepository ledgerEntryRepository;

    public KycLimitServiceImpl(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void validateTransactionLimit(User user, BigDecimal requestedAmount) {
        if (user == null || requestedAmount == null) {
            return;
        }

        KycTierPolicy policy = KycTierPolicy.fromTier(user.getKycTier());

        // 1. Single transaction limit check
        if (requestedAmount.compareTo(policy.getSingleTransactionLimit()) > 0) {
            log.warn("User {} (Tier {}) exceeded single-transaction limit. Requested: {}, Limit: {}",
                    user.getEmail(), user.getKycTier(), requestedAmount, policy.getSingleTransactionLimit());
            throw KycLimitExceededException.singleLimit(user.getKycTier(), requestedAmount, policy.getSingleTransactionLimit());
        }

        // 2. Daily volume limit check
        BigDecimal usedToday = getDailyUsedToday(user.getId());
        BigDecimal projectedTotal = usedToday.add(requestedAmount);

        if (projectedTotal.compareTo(policy.getDailyLimit()) > 0) {
            log.warn("User {} (Tier {}) exceeded daily volume limit. Requested: {}, Used Today: {}, Limit: {}",
                    user.getEmail(), user.getKycTier(), requestedAmount, usedToday, policy.getDailyLimit());
            throw KycLimitExceededException.dailyLimit(user.getKycTier(), requestedAmount, usedToday, policy.getDailyLimit());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getDailyUsedToday(UUID userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        OffsetDateTime startOfTodayUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
        BigDecimal used = ledgerEntryRepository.sumUserAmountByEntryTypeSince(userId, EntryType.DEBIT, startOfTodayUtc);
        return used != null ? used : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getDailyRemainingToday(User user) {
        if (user == null) {
            return BigDecimal.ZERO;
        }
        KycTierPolicy policy = KycTierPolicy.fromTier(user.getKycTier());
        BigDecimal usedToday = getDailyUsedToday(user.getId());
        BigDecimal remaining = policy.getDailyLimit().subtract(usedToday);
        return remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
    }
}

