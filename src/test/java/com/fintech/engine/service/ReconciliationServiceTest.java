package com.fintech.engine.service;

import com.fintech.engine.dto.ReconciliationRunResponse;
import com.fintech.engine.dto.SettlementReconciliationRequest;
import com.fintech.engine.dto.SettlementRecord;
import com.fintech.engine.exception.InvalidReconciliationRequestException;
import com.fintech.engine.exception.ReconciliationRunNotFoundException;
import com.fintech.engine.model.entity.ReconciliationDiscrepancy;
import com.fintech.engine.model.entity.ReconciliationRun;
import com.fintech.engine.model.entity.WebhookEvent;
import com.fintech.engine.model.enums.DiscrepancySeverity;
import com.fintech.engine.model.enums.DiscrepancyType;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.ReconciliationStatus;
import com.fintech.engine.model.enums.ReconciliationType;
import com.fintech.engine.model.enums.WebhookProvider;
import com.fintech.engine.repository.LedgerEntryRepository;
import com.fintech.engine.repository.ReconciliationDiscrepancyRepository;
import com.fintech.engine.repository.ReconciliationQueryRepository;
import com.fintech.engine.repository.ReconciliationRunRepository;
import com.fintech.engine.repository.projection.AccountBalanceView;
import com.fintech.engine.repository.projection.CurrencyAmountView;
import com.fintech.engine.repository.projection.CurrencyTrialBalanceView;
import com.fintech.engine.repository.projection.MissingPayoutPostingView;
import com.fintech.engine.repository.projection.PostedDepositView;
import com.fintech.engine.repository.projection.TransactionImbalanceView;
import com.fintech.engine.service.impl.ReconciliationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private ReconciliationRunRepository runRepository;

    @Mock
    private ReconciliationDiscrepancyRepository discrepancyRepository;

    @Mock
    private ReconciliationQueryRepository queryRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private ReconciliationServiceImpl reconciliationService;

    private final OffsetDateTime periodStart = OffsetDateTime.parse("2026-10-31T00:00:00Z");
    private final OffsetDateTime periodEnd = OffsetDateTime.parse("2026-11-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationServiceImpl(
                runRepository, discrepancyRepository, queryRepository, ledgerEntryRepository);
    }

    private void stubRunPersistence() {
        when(runRepository.save(any(ReconciliationRun.class))).thenAnswer(inv -> {
            ReconciliationRun run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
    }

    @SuppressWarnings("unchecked")
    private void stubDiscrepancyPersistence() {
        when(discrepancyRepository.saveAll(anyCollection())).thenAnswer(inv -> List.copyOf((Collection<ReconciliationDiscrepancy>) inv.getArgument(0)));
    }

    private void stubGlobalTotals(String debits, String credits) {
        when(ledgerEntryRepository.sumTotalByEntryType(EntryType.DEBIT)).thenReturn(new BigDecimal(debits));
        when(ledgerEntryRepository.sumTotalByEntryType(EntryType.CREDIT)).thenReturn(new BigDecimal(credits));
    }

    // ------------------------------------------------------------------------------------------------
    // Internal integrity
    // ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Internal run should PASS when books are balanced and consistent")
    void internalRunShouldPassWhenBooksAreClean() {
        stubRunPersistence();
        stubGlobalTotals("1500.0000", "1500.00");
        when(queryRepository.findTrialBalanceByCurrency())
                .thenReturn(List.of(trialBalance("USD", "1500.00", "1500.0000")));
        when(queryRepository.findCreditNormalBalancesByAccountPrefix("SYS-2002-%"))
                .thenReturn(List.of(currencyAmount("USD", "250.00")));
        when(queryRepository.sumPayoutAmountsByCurrency(anyCollection()))
                .thenReturn(List.of(currencyAmount("USD", "250.0000")));

        ReconciliationRunResponse result = reconciliationService.runInternalReconciliation("admin@fintech.com");

        assertEquals(ReconciliationStatus.PASSED, result.status());
        assertEquals(ReconciliationType.INTERNAL_INTEGRITY, result.type());
        assertEquals(0, result.discrepancyCount());
        assertTrue(result.discrepancies().isEmpty());
        assertEquals(0, new BigDecimal("1500").compareTo(result.totalDebits()));
        assertEquals("admin@fintech.com", result.triggeredBy());
        assertNotNull(result.completedAt());
        verify(discrepancyRepository, never()).saveAll(anyCollection());
    }

    @Test
    @DisplayName("Internal run should FAIL and report trial balance, unbalanced transaction and negative wallet discrepancies")
    void internalRunShouldDetectLedgerCorruption() {
        stubRunPersistence();
        stubDiscrepancyPersistence();
        stubGlobalTotals("1000.00", "990.00");
        UUID walletId = UUID.randomUUID();

        when(queryRepository.findTrialBalanceByCurrency())
                .thenReturn(List.of(trialBalance("USD", "1000.00", "990.00")));
        when(queryRepository.findUnbalancedTransactions())
                .thenReturn(List.of(imbalance("TRF-BROKEN-1", "100.00", "90.00")));
        when(queryRepository.findNegativeWalletBalances())
                .thenReturn(List.of(negativeWallet(walletId, "USD-12345678", "USD", "-25.00")));

        ReconciliationRunResponse result = reconciliationService.runInternalReconciliation("SYSTEM_SCHEDULER");

        assertEquals(ReconciliationStatus.FAILED, result.status());
        assertEquals(4, result.discrepancyCount());
        List<DiscrepancyType> types = result.discrepancies().stream().map(d -> d.type()).toList();
        assertTrue(types.containsAll(List.of(
                DiscrepancyType.GLOBAL_TRIAL_BALANCE_MISMATCH,
                DiscrepancyType.CURRENCY_TRIAL_BALANCE_MISMATCH,
                DiscrepancyType.UNBALANCED_TRANSACTION,
                DiscrepancyType.NEGATIVE_WALLET_BALANCE)));
        assertTrue(result.discrepancies().stream().allMatch(d -> d.severity() == DiscrepancySeverity.CRITICAL));

        var wallet = result.discrepancies().stream()
                .filter(d -> d.type() == DiscrepancyType.NEGATIVE_WALLET_BALANCE).findFirst().orElseThrow();
        assertEquals(walletId, wallet.accountId());
        assertEquals(0, new BigDecimal("-25.00").compareTo(wallet.actualAmount()));
    }

    @Test
    @DisplayName("Internal run should detect escrow balance that does not match in-flight payouts")
    void internalRunShouldDetectEscrowMismatch() {
        stubRunPersistence();
        stubDiscrepancyPersistence();
        stubGlobalTotals("500.00", "500.00");

        when(queryRepository.findCreditNormalBalancesByAccountPrefix("SYS-2002-%"))
                .thenReturn(List.of(currencyAmount("USD", "100.00"), currencyAmount("NGN", "0")));
        when(queryRepository.sumPayoutAmountsByCurrency(anyCollection()))
                .thenReturn(List.of(currencyAmount("USD", "150.00")));

        ReconciliationRunResponse result = reconciliationService.runInternalReconciliation("ops@fintech.com");

        assertEquals(ReconciliationStatus.FAILED, result.status());
        assertEquals(1, result.discrepancyCount());
        var escrow = result.discrepancies().get(0);
        assertEquals(DiscrepancyType.ESCROW_BALANCE_MISMATCH, escrow.type());
        assertEquals("USD", escrow.currency());
        assertEquals(0, new BigDecimal("150.00").compareTo(escrow.expectedAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(escrow.actualAmount()));
    }

    @Test
    @DisplayName("Internal run should detect payouts and processed webhooks missing their journal postings")
    void internalRunShouldDetectMissingPostings() {
        stubRunPersistence();
        stubDiscrepancyPersistence();
        stubGlobalTotals("0", "0");

        WebhookEvent webhook = new WebhookEvent(WebhookProvider.STRIPE, "evt_123", "charge.succeeded", "{}");
        when(queryRepository.findPayoutsMissingPostings())
                .thenReturn(List.of(missingPayout("PAY-001", "COMPLETED", "75.00", "USD", "SETTLE-PAY-001")));
        when(queryRepository.findProcessedWebhooksWithoutPosting()).thenReturn(List.of(webhook));

        ReconciliationRunResponse result = reconciliationService.runInternalReconciliation("ops@fintech.com");

        assertEquals(ReconciliationStatus.FAILED, result.status());
        assertEquals(2, result.discrepancyCount());

        var payout = result.discrepancies().stream()
                .filter(d -> d.type() == DiscrepancyType.MISSING_PAYOUT_POSTING).findFirst().orElseThrow();
        assertEquals("PAY-001", payout.reference());
        assertEquals(DiscrepancySeverity.HIGH, payout.severity());
        assertTrue(payout.details().contains("SETTLE-PAY-001"));

        var deposit = result.discrepancies().stream()
                .filter(d -> d.type() == DiscrepancyType.MISSING_DEPOSIT_POSTING).findFirst().orElseThrow();
        assertEquals("DEP-STRIPE-evt_123", deposit.reference());
    }

    @Test
    @DisplayName("Internal run should be persisted with ERROR status when a check throws")
    void internalRunShouldRecordErrorOnFailure() {
        stubRunPersistence();
        stubGlobalTotals("10.00", "10.00");
        when(queryRepository.findTrialBalanceByCurrency()).thenThrow(new IllegalStateException("connection reset"));

        ReconciliationRunResponse result = reconciliationService.runInternalReconciliation("ops@fintech.com");

        assertEquals(ReconciliationStatus.ERROR, result.status());
        assertTrue(result.errorMessage().contains("connection reset"));
        assertNotNull(result.completedAt());
        verify(runRepository, times(2)).save(any(ReconciliationRun.class));
        verify(discrepancyRepository, never()).saveAll(anyCollection());
    }

    // ------------------------------------------------------------------------------------------------
    // Gateway settlement
    // ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Settlement run should PASS when every settled payment matches the ledger exactly")
    void settlementRunShouldPassWhenAllRecordsMatch() {
        stubRunPersistence();
        SettlementReconciliationRequest request = new SettlementReconciliationRequest(periodStart, periodEnd, List.of(
                new SettlementRecord("evt_1", new BigDecimal("100.00"), "usd"),
                new SettlementRecord("evt_2", new BigDecimal("50.00"), "USD")
        ));

        when(queryRepository.findPostingsByReferences(anyCollection())).thenReturn(List.of(
                deposit("DEP-STRIPE-evt_1", "100.0000", "USD"),
                deposit("DEP-STRIPE-evt_2", "50.0000", "USD")
        ));
        when(queryRepository.findPostingsInPeriod(eq("DEP-STRIPE-%"), eq(periodStart), eq(periodEnd))).thenReturn(List.of(
                deposit("DEP-STRIPE-evt_1", "100.0000", "USD"),
                deposit("DEP-STRIPE-evt_2", "50.0000", "USD")
        ));

        ReconciliationRunResponse result = reconciliationService.runSettlementReconciliation(
                WebhookProvider.STRIPE, request, "admin@fintech.com");

        assertEquals(ReconciliationStatus.PASSED, result.status());
        assertEquals(ReconciliationType.GATEWAY_SETTLEMENT, result.type());
        assertEquals(WebhookProvider.STRIPE, result.provider());
        assertEquals(2, result.recordsChecked());
        assertEquals(0, new BigDecimal("150").compareTo(result.externalTotal()));
        assertEquals(0, new BigDecimal("150").compareTo(result.ledgerTotal()));
        assertEquals(periodStart, result.periodStart());
    }

    @Test
    @DisplayName("Settlement run should detect missing, ghost, amount, currency and duplicate discrepancies")
    void settlementRunShouldDetectAllDiscrepancyKinds() {
        stubRunPersistence();
        stubDiscrepancyPersistence();
        SettlementReconciliationRequest request = new SettlementReconciliationRequest(periodStart, periodEnd, List.of(
                new SettlementRecord("evt_ok", new BigDecimal("10.00"), "USD"),
                new SettlementRecord("evt_amount", new BigDecimal("20.00"), "USD"),
                new SettlementRecord("evt_currency", new BigDecimal("30.00"), "EUR"),
                new SettlementRecord("evt_missing", new BigDecimal("40.00"), "USD"),
                new SettlementRecord("evt_ok", new BigDecimal("10.00"), "USD")
        ));

        when(queryRepository.findPostingsByReferences(anyCollection())).thenReturn(List.of(
                deposit("DEP-PAYSTACK-evt_ok", "10.00", "USD"),
                deposit("DEP-PAYSTACK-evt_amount", "25.00", "USD"),
                deposit("DEP-PAYSTACK-evt_currency", "30.00", "USD")
        ));
        when(queryRepository.findPostingsInPeriod(eq("DEP-PAYSTACK-%"), any(), any())).thenReturn(List.of(
                deposit("DEP-PAYSTACK-evt_ok", "10.00", "USD"),
                deposit("DEP-PAYSTACK-evt_amount", "25.00", "USD"),
                deposit("DEP-PAYSTACK-evt_ghost", "99.00", "USD")
        ));

        ReconciliationRunResponse result = reconciliationService.runSettlementReconciliation(
                WebhookProvider.PAYSTACK, request, "admin@fintech.com");

        assertEquals(ReconciliationStatus.FAILED, result.status());
        assertEquals(5, result.discrepancyCount());
        assertEquals(5, result.recordsChecked());

        assertDiscrepancy(result, DiscrepancyType.DUPLICATE_SETTLEMENT_RECORD, "evt_ok");
        assertDiscrepancy(result, DiscrepancyType.AMOUNT_MISMATCH, "evt_amount");
        assertDiscrepancy(result, DiscrepancyType.CURRENCY_MISMATCH, "evt_currency");
        assertDiscrepancy(result, DiscrepancyType.MISSING_IN_LEDGER, "evt_missing");
        assertDiscrepancy(result, DiscrepancyType.GHOST_IN_LEDGER, "evt_ghost");

        // External total includes the duplicate line as reported by the provider
        assertEquals(0, new BigDecimal("110.00").compareTo(result.externalTotal()));
        assertEquals(0, new BigDecimal("134.00").compareTo(result.ledgerTotal()));
    }

    @Test
    @DisplayName("Settlement run with an empty report should flag every ledger deposit in the window as a ghost")
    void settlementRunWithEmptyReportShouldFlagGhosts() {
        stubRunPersistence();
        stubDiscrepancyPersistence();
        SettlementReconciliationRequest request = new SettlementReconciliationRequest(periodStart, periodEnd, List.of());

        when(queryRepository.findPostingsInPeriod(eq("DEP-FLUTTERWAVE-%"), any(), any()))
                .thenReturn(List.of(deposit("DEP-FLUTTERWAVE-flw_9", "12.50", "NGN")));

        ReconciliationRunResponse result = reconciliationService.runSettlementReconciliation(
                WebhookProvider.FLUTTERWAVE, request, "admin@fintech.com");

        assertEquals(ReconciliationStatus.FAILED, result.status());
        assertDiscrepancy(result, DiscrepancyType.GHOST_IN_LEDGER, "flw_9");
        verify(queryRepository, never()).findPostingsByReferences(anyCollection());
    }

    @Test
    @DisplayName("Settlement run should reject a period whose end is not after its start")
    void settlementRunShouldRejectInvalidPeriod() {
        SettlementReconciliationRequest request = new SettlementReconciliationRequest(periodEnd, periodStart, List.of());

        assertThrows(InvalidReconciliationRequestException.class, () ->
                reconciliationService.runSettlementReconciliation(WebhookProvider.STRIPE, request, "admin@fintech.com"));

        verify(runRepository, never()).save(any());
    }

    // ------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getRun should throw when the run does not exist")
    void getRunShouldThrowWhenMissing() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        assertThrows(ReconciliationRunNotFoundException.class, () -> reconciliationService.getRun(runId));
    }

    @Test
    @DisplayName("listRuns should reject out-of-range page sizes")
    void listRunsShouldRejectInvalidPaging() {
        assertThrows(InvalidReconciliationRequestException.class, () -> reconciliationService.listRuns(0, 0));
        assertThrows(InvalidReconciliationRequestException.class, () -> reconciliationService.listRuns(0, 500));
        assertThrows(InvalidReconciliationRequestException.class, () -> reconciliationService.listRuns(-1, 20));
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------

    private static void assertDiscrepancy(ReconciliationRunResponse result, DiscrepancyType type, String reference) {
        assertTrue(result.discrepancies().stream().anyMatch(d -> d.type() == type && reference.equals(d.reference())),
                "Expected " + type + " for " + reference + " but got " + result.discrepancies());
    }

    private static CurrencyTrialBalanceView trialBalance(String currency, String debits, String credits) {
        return new CurrencyTrialBalanceView() {
            public String getCurrency() { return currency; }
            public BigDecimal getDebits() { return new BigDecimal(debits); }
            public BigDecimal getCredits() { return new BigDecimal(credits); }
        };
    }

    private static TransactionImbalanceView imbalance(String reference, String debits, String credits) {
        return new TransactionImbalanceView() {
            public String getReference() { return reference; }
            public BigDecimal getDebits() { return new BigDecimal(debits); }
            public BigDecimal getCredits() { return new BigDecimal(credits); }
        };
    }

    private static AccountBalanceView negativeWallet(UUID id, String number, String currency, String balance) {
        return new AccountBalanceView() {
            public UUID getAccountId() { return id; }
            public String getAccountNumber() { return number; }
            public String getCurrency() { return currency; }
            public BigDecimal getBalance() { return new BigDecimal(balance); }
        };
    }

    private static CurrencyAmountView currencyAmount(String currency, String amount) {
        return new CurrencyAmountView() {
            public String getCurrency() { return currency; }
            public BigDecimal getAmount() { return new BigDecimal(amount); }
        };
    }

    private static MissingPayoutPostingView missingPayout(String reference, String status, String amount,
                                                          String currency, String expectedPosting) {
        return new MissingPayoutPostingView() {
            public String getReference() { return reference; }
            public String getStatus() { return status; }
            public BigDecimal getAmount() { return new BigDecimal(amount); }
            public String getCurrency() { return currency; }
            public String getExpectedPosting() { return expectedPosting; }
        };
    }

    private static PostedDepositView deposit(String reference, String amount, String currency) {
        return new PostedDepositView() {
            public String getReference() { return reference; }
            public BigDecimal getAmount() { return new BigDecimal(amount); }
            public String getCurrency() { return currency; }
        };
    }
}

