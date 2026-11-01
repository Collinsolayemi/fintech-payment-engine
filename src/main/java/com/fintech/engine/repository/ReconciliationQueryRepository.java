package com.fintech.engine.repository;

import com.fintech.engine.model.entity.ReconciliationRun;
import com.fintech.engine.model.entity.WebhookEvent;
import com.fintech.engine.model.enums.PayoutStatus;
import com.fintech.engine.repository.projection.AccountBalanceView;
import com.fintech.engine.repository.projection.CurrencyAmountView;
import com.fintech.engine.repository.projection.CurrencyTrialBalanceView;
import com.fintech.engine.repository.projection.MissingPayoutPostingView;
import com.fintech.engine.repository.projection.PostedDepositView;
import com.fintech.engine.repository.projection.TransactionImbalanceView;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Read-only aggregate queries used by the reconciliation engine.
 * Kept separate from transactional repositories so audit SQL lives in one reviewable place.
 * Column aliases are double-quoted so projection mapping is case-exact on PostgreSQL and H2.
 */
@org.springframework.stereotype.Repository
public interface ReconciliationQueryRepository extends org.springframework.data.repository.Repository<ReconciliationRun, java.util.UUID> {

    /** Trial balance grouped by currency. */
    @Query(value = """
            SELECT a.currency AS "currency",
                   COALESCE(SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END), 0) AS "debits",
                   COALESCE(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END), 0) AS "credits"
            FROM ledger_entries le
            JOIN accounts a ON a.id = le.account_id
            GROUP BY a.currency
            """, nativeQuery = true)
    List<CurrencyTrialBalanceView> findTrialBalanceByCurrency();

    /** Journal transactions whose debits and credits do not net to zero. */
    @Query(value = """
            SELECT t.reference AS "reference",
                   SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) AS "debits",
                   SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) AS "credits"
            FROM ledger_entries le
            JOIN transactions t ON t.id = le.transaction_id
            GROUP BY t.id, t.reference
            HAVING SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END)
                <> SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END)
            """, nativeQuery = true)
    List<TransactionImbalanceView> findUnbalancedTransactions();

    /** Customer wallets (user-owned LIABILITY accounts) whose derived balance is negative. */
    @Query(value = """
            SELECT a.id AS "accountId",
                   a.account_number AS "accountNumber",
                   a.currency AS "currency",
                   SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE -le.amount END) AS "balance"
            FROM accounts a
            JOIN ledger_entries le ON le.account_id = a.id
            WHERE a.user_id IS NOT NULL AND a.type = 'LIABILITY'
            GROUP BY a.id, a.account_number, a.currency
            HAVING SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE -le.amount END) < 0
            """, nativeQuery = true)
    List<AccountBalanceView> findNegativeWalletBalances();

    /**
     * Credit-normal balances of accounts matching an account-number prefix, grouped by currency.
     * Used for the PENDING_SETTLEMENTS escrow (SYS-2002-*) check.
     */
    @Query(value = """
            SELECT a.currency AS "currency",
                   COALESCE(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount
                                     WHEN le.entry_type = 'DEBIT' THEN -le.amount
                                     ELSE 0 END), 0) AS "amount"
            FROM accounts a
            LEFT JOIN ledger_entries le ON le.account_id = a.id
            WHERE a.account_number LIKE :accountNumberPrefix
            GROUP BY a.currency
            """, nativeQuery = true)
    List<CurrencyAmountView> findCreditNormalBalancesByAccountPrefix(@Param("accountNumberPrefix") String accountNumberPrefix);

    /** Sum of payout principal per currency for the given statuses. */
    @Query("SELECT p.currency AS currency, SUM(p.amount) AS amount FROM Payout p WHERE p.status IN :statuses GROUP BY p.currency")
    List<CurrencyAmountView> sumPayoutAmountsByCurrency(@Param("statuses") Collection<PayoutStatus> statuses);

    /** Payouts missing the journal posting required by their lifecycle state. */
    @Query(value = """
            SELECT p.reference AS "reference", p.status AS "status", p.amount AS "amount",
                   p.currency AS "currency", CONCAT('ESCROW-', p.reference) AS "expectedPosting"
            FROM payouts p
            WHERE NOT EXISTS (SELECT 1 FROM transactions t WHERE t.reference = CONCAT('ESCROW-', p.reference))
            UNION ALL
            SELECT p.reference, p.status, p.amount, p.currency, CONCAT('SETTLE-', p.reference)
            FROM payouts p
            WHERE p.status = 'COMPLETED'
              AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.reference = CONCAT('SETTLE-', p.reference))
            UNION ALL
            SELECT p.reference, p.status, p.amount, p.currency, CONCAT('REV-', p.reference)
            FROM payouts p
            WHERE p.status = 'REVERSED'
              AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.reference = CONCAT('REV-', p.reference))
            """, nativeQuery = true)
    List<MissingPayoutPostingView> findPayoutsMissingPostings();

    /** Webhooks marked PROCESSED with no DEP-{provider}-{eventId} journal posting. */
    @Query(value = """
            SELECT w.* FROM webhook_events w
            WHERE w.status = 'PROCESSED'
              AND NOT EXISTS (
                  SELECT 1 FROM transactions t
                  WHERE t.reference = CONCAT('DEP-', w.provider, '-', w.event_id)
              )
            """, nativeQuery = true)
    List<WebhookEvent> findProcessedWebhooksWithoutPosting();

    /** Deposit postings (credited amount + currency) for the given journal references. */
    @Query(value = """
            SELECT t.reference AS "reference",
                   SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) AS "amount",
                   MAX(a.currency) AS "currency"
            FROM transactions t
            JOIN ledger_entries le ON le.transaction_id = t.id
            JOIN accounts a ON a.id = le.account_id
            WHERE t.reference IN (:references)
            GROUP BY t.reference
            """, nativeQuery = true)
    List<PostedDepositView> findPostingsByReferences(@Param("references") Collection<String> references);

    /** Deposit postings whose reference starts with the prefix and were booked inside [from, to). */
    @Query(value = """
            SELECT t.reference AS "reference",
                   SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) AS "amount",
                   MAX(a.currency) AS "currency"
            FROM transactions t
            JOIN ledger_entries le ON le.transaction_id = t.id
            JOIN accounts a ON a.id = le.account_id
            WHERE t.reference LIKE :referencePrefix
              AND t.created_at >= :periodStart
              AND t.created_at < :periodEnd
            GROUP BY t.reference
            """, nativeQuery = true)
    List<PostedDepositView> findPostingsInPeriod(@Param("referencePrefix") String referencePrefix,
                                                 @Param("periodStart") OffsetDateTime periodStart,
                                                 @Param("periodEnd") OffsetDateTime periodEnd);
}

