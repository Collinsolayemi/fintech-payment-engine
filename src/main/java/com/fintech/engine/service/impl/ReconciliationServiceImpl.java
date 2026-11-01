package com.fintech.engine.service.impl;

import com.fintech.engine.dto.ReconciliationDiscrepancyResponse;
import com.fintech.engine.dto.ReconciliationRunResponse;
import com.fintech.engine.dto.SettlementReconciliationRequest;
import com.fintech.engine.dto.SettlementRecord;
import com.fintech.engine.exception.InvalidReconciliationRequestException;
import com.fintech.engine.exception.ReconciliationRunNotFoundException;
import com.fintech.engine.model.entity.ReconciliationDiscrepancy;
import com.fintech.engine.model.entity.ReconciliationRun;
import com.fintech.engine.model.entity.WebhookEvent;
import com.fintech.engine.model.enums.DiscrepancyType;
import com.fintech.engine.model.enums.EntryType;
import com.fintech.engine.model.enums.PayoutStatus;
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
import com.fintech.engine.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Reconciliation engine.
 *
 * The orchestration methods are intentionally NOT wrapped in a single transaction: the run header is
 * committed as RUNNING first, so a crash mid-run still leaves an auditable ERROR/RUNNING record rather
 * than silently rolling the evidence away. All checks are read-only.
 */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    static final String ESCROW_ACCOUNT_PREFIX = "SYS-2002-";
    /** Payout states in which principal is still held in PENDING_SETTLEMENTS escrow. */
    static final Set<PayoutStatus> ESCROW_HOLDING_STATUSES =
            EnumSet.of(PayoutStatus.PENDING_ESCROW, PayoutStatus.PROCESSING, PayoutStatus.FAILED);
    /** Bound the SQL IN-list size for large settlement files. */
    private static final int REFERENCE_BATCH_SIZE = 1000;
    private static final int MAX_PAGE_SIZE = 100;

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final ReconciliationQueryRepository queryRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReconciliationServiceImpl(
        ReconciliationRunRepository runRepository,
        ReconciliationDiscrepancyRepository discrepancyRepository,
        ReconciliationQueryRepository queryRepository,
        LedgerEntryRepository ledgerEntryRepository
    ) {
        this.runRepository = runRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.queryRepository = queryRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    // ------------------------------------------------------------------------------------------------
    // Internal integrity
    // ------------------------------------------------------------------------------------------------

    @Override
    public ReconciliationRunResponse runInternalReconciliation(String triggeredBy) {
        log.info("Starting internal ledger reconciliation (triggered by {})", triggeredBy);
        ReconciliationRun run = runRepository.save(
                new ReconciliationRun(ReconciliationType.INTERNAL_INTEGRITY, null, triggeredBy));

        try {
            List<ReconciliationDiscrepancy> found = new ArrayList<>();

            checkGlobalTrialBalance(run, found);
            checkCurrencyTrialBalances(run, found);
            checkTransactionBalances(run, found);
            checkNegativeWalletBalances(run, found);
            checkEscrowBalances(run, found);
            checkPayoutPostings(run, found);
            checkDepositPostings(run, found);

            return finish(run, found);
        } catch (RuntimeException ex) {
            return fail(run, ex);
        }
    }

    private void checkGlobalTrialBalance(ReconciliationRun run, List<ReconciliationDiscrepancy> found) {
        BigDecimal debits = nz(ledgerEntryRepository.sumTotalByEntryType(EntryType.DEBIT));
        BigDecimal credits = nz(ledgerEntryRepository.sumTotalByEntryType(EntryType.CREDIT));
        run.setTotalDebits(debits);
        run.setTotalCredits(credits);

        if (debits.compareTo(credits) != 0) {
            found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.GLOBAL_TRIAL_BALANCE_MISMATCH,
                    null, null, null, debits, credits,
                    "Platform total debits (" + debits + ") != total credits (" + credits + ")"));
        }
    }

    private void checkCurrencyTrialBalances(ReconciliationRun run, List<ReconciliationDiscrepancy> found) {
        for (CurrencyTrialBalanceView row : queryRepository.findTrialBalanceByCurrency()) {
            BigDecimal debits = nz(row.getDebits());
            BigDecimal credits = nz(row.getCredits());
            if (debits.compareTo(credits) != 0) {
                found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.CURRENCY_TRIAL_BALANCE_MISMATCH,
                        null, null, row.getCurrency(), debits, credits,
                        "Trial balance for " + row.getCurrency() + " is off by " + debits.subtract(credits)));
            }
        }
    }

    private void checkTransactionBalances(ReconciliationRun run, List<ReconciliationDiscrepancy> found) {
        for (TransactionImbalanceView row : queryRepository.findUnbalancedTransactions()) {
            found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.UNBALANCED_TRANSACTION,
                    row.getReference(), null, null, nz(row.getDebits()), nz(row.getCredits()),
                    "Journal transaction " + row.getReference() + " debits " + nz(row.getDebits())
                            + " != credits " + nz(row.getCredits())));
        }
    }

    private void checkNegativeWalletBalances(ReconciliationRun run, List<ReconciliationDiscrepancy> found) {
        for (AccountBalanceView row : queryRepository.findNegativeWalletBalances()) {
            found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.NEGATIVE_WALLET_BALANCE,
                    row.getAccountNumber(), row.getAccountId(), row.getCurrency(), BigDecimal.ZERO, row.getBalance(),
                    "Customer wallet " + row.getAccountNumber() + " has negative derived balance " + row.getBalance()));
        }
    }

    private void checkEscrowBalances(ReconciliationRun run, List<ReconciliationDiscrepancy> found) {
        Map<String, BigDecimal> escrowBalances =
                toCurrencyMap(queryRepository.findCreditNormalBalancesByAccountPrefix(ESCROW_ACCOUNT_PREFIX + "%"));
        Map<String, BigDecimal> inFlightPayouts =
                toCurrencyMap(queryRepository.sumPayoutAmountsByCurrency(ESCROW_HOLDING_STATUSES));

        Set<String> currencies = new TreeSet<>(escrowBalances.keySet());
        currencies.addAll(inFlightPayouts.keySet());

        for (String currency : currencies) {
            BigDecimal expected = inFlightPayouts.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal actual = escrowBalances.getOrDefault(currency, BigDecimal.ZERO);
            if (expected.compareTo(actual) != 0) {
                found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.ESCROW_BALANCE_MISMATCH,
                        ESCROW_ACCOUNT_PREFIX + currency, null, currency, expected, actual,
                        "PENDING_SETTLEMENTS escrow holds " + actual + " " + currency
                                + " but in-flight payouts total " + expected));
            }
        }
    }

    private void checkPayoutPostings(ReconciliationRun run, List<ReconciliationDiscrepancy> found) {
        for (MissingPayoutPostingView row : queryRepository.findPayoutsMissingPostings()) {
            found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.MISSING_PAYOUT_POSTING,
                    row.getReference(), null, row.getCurrency(), row.getAmount(), null,
                    "Payout " + row.getReference() + " in status " + row.getStatus()
                            + " is missing journal posting " + row.getExpectedPosting()));
        }
    }

    private void checkDepositPostings(ReconciliationRun run, List<ReconciliationDiscrepancy> found) {
        for (WebhookEvent event : queryRepository.findProcessedWebhooksWithoutPosting()) {
            String expectedRef = depositReference(event.getProvider(), event.getEventId());
            found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.MISSING_DEPOSIT_POSTING,
                    expectedRef, null, null, null, null,
                    "Webhook " + event.getProvider() + "/" + event.getEventId()
                            + " is PROCESSED but journal posting " + expectedRef + " does not exist"));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Gateway settlement
    // ------------------------------------------------------------------------------------------------

    @Override
    public ReconciliationRunResponse runSettlementReconciliation(WebhookProvider provider,
                                                                 SettlementReconciliationRequest request,
                                                                 String triggeredBy) {
        if (provider == null) {
            throw new InvalidReconciliationRequestException("Provider is required");
        }
        if (!request.periodEnd().isAfter(request.periodStart())) {
            throw new InvalidReconciliationRequestException("periodEnd must be after periodStart");
        }

        log.info("Starting {} settlement reconciliation for [{}, {}) with {} records (triggered by {})",
                provider, request.periodStart(), request.periodEnd(), request.records().size(), triggeredBy);

        ReconciliationRun run = new ReconciliationRun(ReconciliationType.GATEWAY_SETTLEMENT, provider, triggeredBy);
        run.setPeriodStart(request.periodStart());
        run.setPeriodEnd(request.periodEnd());
        run.setRecordsChecked(request.records().size());
        run = runRepository.save(run);

        try {
            List<ReconciliationDiscrepancy> found = new ArrayList<>();

            // 1. Index settlement records by ledger reference, flagging duplicates
            Map<String, SettlementRecord> recordsByLedgerRef = new LinkedHashMap<>();
            BigDecimal externalTotal = BigDecimal.ZERO;
            for (SettlementRecord record : request.records()) {
                externalTotal = externalTotal.add(record.amount());
                String ledgerRef = depositReference(provider, record.reference());
                if (recordsByLedgerRef.putIfAbsent(ledgerRef, record) != null) {
                    found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.DUPLICATE_SETTLEMENT_RECORD,
                            record.reference(), null, upper(record.currency()), null, record.amount(),
                            "Settlement reference " + record.reference() + " appears more than once in the report"));
                }
            }
            run.setExternalTotal(externalTotal);

            // 2. Look up ledger postings for every settled reference (regardless of booking date)
            Map<String, PostedDepositView> postingsByRef = findPostingsByReferences(recordsByLedgerRef.keySet());

            // 3. Provider -> Ledger: missing / amount / currency mismatches
            for (Map.Entry<String, SettlementRecord> entry : recordsByLedgerRef.entrySet()) {
                String ledgerRef = entry.getKey();
                SettlementRecord record = entry.getValue();
                String recordCurrency = upper(record.currency());
                PostedDepositView posting = postingsByRef.get(ledgerRef);

                if (posting == null) {
                    found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.MISSING_IN_LEDGER,
                            record.reference(), null, recordCurrency, record.amount(), null,
                            provider + " settled " + record.amount() + " " + recordCurrency
                                    + " for " + record.reference() + " but ledger posting " + ledgerRef + " does not exist"));
                    continue;
                }

                BigDecimal ledgerAmount = nz(posting.getAmount());
                if (ledgerAmount.compareTo(record.amount()) != 0) {
                    found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.AMOUNT_MISMATCH,
                            record.reference(), null, recordCurrency, record.amount(), ledgerAmount,
                            "Provider settled " + record.amount() + " but ledger credited " + ledgerAmount
                                    + " for " + ledgerRef));
                }

                if (posting.getCurrency() != null && !posting.getCurrency().equalsIgnoreCase(recordCurrency)) {
                    found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.CURRENCY_MISMATCH,
                            record.reference(), null, recordCurrency, record.amount(), ledgerAmount,
                            "Provider settled in " + recordCurrency + " but ledger posting " + ledgerRef
                                    + " is in " + posting.getCurrency()));
                }
            }

            // 4. Ledger -> Provider: ghost deposits booked in the window but absent from the report
            BigDecimal ledgerTotal = BigDecimal.ZERO;
            String prefix = depositReference(provider, "");
            for (PostedDepositView posting : queryRepository.findPostingsInPeriod(
                    prefix + "%", request.periodStart(), request.periodEnd())) {
                ledgerTotal = ledgerTotal.add(nz(posting.getAmount()));
                if (!recordsByLedgerRef.containsKey(posting.getReference())) {
                    String externalRef = posting.getReference().substring(prefix.length());
                    found.add(new ReconciliationDiscrepancy(run.getId(), DiscrepancyType.GHOST_IN_LEDGER,
                            externalRef, null, posting.getCurrency(), null, nz(posting.getAmount()),
                            "Ledger posting " + posting.getReference() + " credited " + nz(posting.getAmount())
                                    + " but " + provider + " did not settle it in this period"));
                }
            }
            run.setLedgerTotal(ledgerTotal);

            return finish(run, found);
        } catch (RuntimeException ex) {
            return fail(run, ex);
        }
    }

    private Map<String, PostedDepositView> findPostingsByReferences(Collection<String> references) {
        Map<String, PostedDepositView> result = new HashMap<>();
        List<String> refs = new ArrayList<>(references);
        for (int from = 0; from < refs.size(); from += REFERENCE_BATCH_SIZE) {
            List<String> batch = refs.subList(from, Math.min(from + REFERENCE_BATCH_SIZE, refs.size()));
            for (PostedDepositView posting : queryRepository.findPostingsByReferences(batch)) {
                result.put(posting.getReference(), posting);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ReconciliationRunResponse getRun(UUID runId) {
        ReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ReconciliationRunNotFoundException(runId));
        List<ReconciliationDiscrepancyResponse> discrepancies = discrepancyRepository
                .findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(this::toResponse)
                .toList();
        return toResponse(run, discrepancies);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconciliationRunResponse> listRuns(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidReconciliationRequestException(
                    "page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size)).stream()
                .map(run -> toResponse(run, null))
                .toList();
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------

    private ReconciliationRunResponse finish(ReconciliationRun run, List<ReconciliationDiscrepancy> found) {
        List<ReconciliationDiscrepancy> saved = found.isEmpty() ? List.of() : discrepancyRepository.saveAll(found);
        run.complete(saved.size());
        run = runRepository.save(run);

        if (saved.isEmpty()) {
            log.info("Reconciliation run {} ({}) PASSED", run.getId(), run.getType());
        } else {
            log.error("CRITICAL RECONCILIATION FAILURE: run {} ({}) detected {} discrepancies",
                    run.getId(), run.getType(), saved.size());
        }
        return toResponse(run, saved.stream().map(this::toResponse).toList());
    }

    private ReconciliationRunResponse fail(ReconciliationRun run, RuntimeException ex) {
        log.error("Reconciliation run {} ({}) aborted with error", run.getId(), run.getType(), ex);
        run.markError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        run = runRepository.save(run);
        return toResponse(run, List.of());
    }

    static String depositReference(WebhookProvider provider, String externalReference) {
        // Must mirror the posting reference format used by WebhookServiceImpl
        return "DEP-" + provider.name() + "-" + externalReference;
    }

    private static Map<String, BigDecimal> toCurrencyMap(List<CurrencyAmountView> rows) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (CurrencyAmountView row : rows) {
            map.merge(upper(row.getCurrency()), nz(row.getAmount()), BigDecimal::add);
        }
        return map;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String upper(String value) {
        return value != null ? value.toUpperCase(Locale.ROOT) : null;
    }

    private ReconciliationRunResponse toResponse(ReconciliationRun run, List<ReconciliationDiscrepancyResponse> discrepancies) {
        return new ReconciliationRunResponse(
                run.getId(),
                run.getType(),
                run.getStatus(),
                run.getProvider(),
                run.getTriggeredBy(),
                run.getPeriodStart(),
                run.getPeriodEnd(),
                run.getTotalDebits(),
                run.getTotalCredits(),
                run.getExternalTotal(),
                run.getLedgerTotal(),
                run.getRecordsChecked(),
                run.getDiscrepancyCount(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getCompletedAt(),
                discrepancies
        );
    }

    private ReconciliationDiscrepancyResponse toResponse(ReconciliationDiscrepancy d) {
        return new ReconciliationDiscrepancyResponse(
                d.getId(),
                d.getType(),
                d.getSeverity(),
                d.getReference(),
                d.getAccountId(),
                d.getCurrency(),
                d.getExpectedAmount(),
                d.getActualAmount(),
                d.getDetails(),
                d.getCreatedAt()
        );
    }
}

