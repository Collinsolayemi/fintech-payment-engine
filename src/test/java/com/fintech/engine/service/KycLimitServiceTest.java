package com.fintech.engine.service;

import com.fintech.engine.exception.KycLimitExceededException;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.repository.LedgerEntryRepository;
import com.fintech.engine.service.impl.KycLimitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycLimitServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private KycLimitServiceImpl kycLimitService;

    private User tier1User;
    private User tier2User;
    private UUID userId;

    @BeforeEach
    void setUp() {
        kycLimitService = new KycLimitServiceImpl(ledgerEntryRepository);
        userId = UUID.randomUUID();

        tier1User = new User("tier1@example.com", "pass", "John", "Doe");
        tier1User.setId(userId);
        tier1User.setKycTier(1);

        tier2User = new User("tier2@example.com", "pass", "Jane", "Doe");
        tier2User.setId(UUID.randomUUID());
        tier2User.setKycTier(2);
    }

    @Test
    @DisplayName("Tier 1 - Should allow transfer within single ($500) and daily ($1000) limits")
    void shouldAllowTransferWithinLimits() {
        when(ledgerEntryRepository.sumUserAmountByEntryTypeSince(eq(userId), eq(EntryType.DEBIT), any()))
                .thenReturn(new BigDecimal("100.00"));

        assertDoesNotThrow(() -> kycLimitService.validateTransactionLimit(tier1User, new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("Tier 1 - Should reject transfer exceeding single transaction limit of $500")
    void shouldRejectExceedingSingleLimit() {
        KycLimitExceededException ex = assertThrows(KycLimitExceededException.class, () ->
                kycLimitService.validateTransactionLimit(tier1User, new BigDecimal("500.01")));

        assertTrue(ex.getMessage().contains("single-transaction limit of 500.00"));
    }

    @Test
    @DisplayName("Tier 1 - Should reject transfer exceeding daily limit of $1000 when combined with today's usage")
    void shouldRejectExceedingDailyLimit() {
        when(ledgerEntryRepository.sumUserAmountByEntryTypeSince(eq(userId), eq(EntryType.DEBIT), any()))
                .thenReturn(new BigDecimal("800.00"));

        KycLimitExceededException ex = assertThrows(KycLimitExceededException.class, () ->
                kycLimitService.validateTransactionLimit(tier1User, new BigDecimal("250.00")));

        assertTrue(ex.getMessage().contains("daily limit of 1000.00"));
        assertTrue(ex.getMessage().contains("already used today: 800.00"));
    }

    @Test
    @DisplayName("Tier 2 - Should allow higher transaction limit ($5000)")
    void shouldAllowHigherLimitForTier2() {
        when(ledgerEntryRepository.sumUserAmountByEntryTypeSince(any(), eq(EntryType.DEBIT), any()))
                .thenReturn(BigDecimal.ZERO);

        assertDoesNotThrow(() -> kycLimitService.validateTransactionLimit(tier2User, new BigDecimal("4500.00")));
    }

    @Test
    @DisplayName("Should correctly calculate remaining daily limit")
    void shouldCalculateRemainingDailyLimit() {
        when(ledgerEntryRepository.sumUserAmountByEntryTypeSince(eq(userId), eq(EntryType.DEBIT), any()))
                .thenReturn(new BigDecimal("400.00"));

        BigDecimal remaining = kycLimitService.getDailyRemainingToday(tier1User);
        assertEquals(0, new BigDecimal("600.00").compareTo(remaining));
    }
}

