# Double-Entry Ledger Specification

This document defines the formal bookkeeping rules, account classifications, and journal entry formats used across the fintech backend.

---

## 1. Accounting Equation & Account Types

All financial activity is structured according to the fundamental accounting equation:

$$\text{Assets} = \text{Liabilities} + \text{Equity}$$

Extended with income and expenses:

$$\text{Assets} + \text{Expenses} = \text{Liabilities} + \text{Equity} + \text{Revenue}$$

### Account Classifications

| Account Classification | Normal Balance | Increase With | Decrease With | Platform Context |
| :--- | :--- | :--- | :--- | :--- |
| **ASSET** | Debit | **Debit** | Credit | Cash at bank, processor receivables, funds held with Stripe/Paystack |
| **LIABILITY** | Credit | **Credit** | Debit | User wallet balances (money the platform owes users), escrow |
| **EQUITY** | Credit | **Credit** | Debit | Shareholder equity, retained earnings |
| **REVENUE** | Credit | **Credit** | Debit | Transaction fees, subscription charges earned |
| **EXPENSE** | Debit | **Debit** | Credit | Gateway processing fees, SMS/email delivery costs |

---

## 2. Standard Chart of Accounts (COA)

Every system instance initializes with the following baseline accounts:

| Account Code | Name | Classification | Currency | Description |
| :--- | :--- | :--- | :--- | :--- |
| `1001` | `CASH_BANK_MAIN` | ASSET | USD | Primary commercial settlement bank account |
| `1002` | `GATEWAY_RECEIVABLE_STRIPE` | ASSET | USD | Funds collected via Stripe pending settlement |
| `2001` | `USER_WALLETS` | LIABILITY | USD | Aggregated customer wallet balances |
| `2002` | `PENDING_SETTLEMENTS` | LIABILITY | USD | Funds held temporarily during outbound payouts |
| `4001` | `REVENUE_TRANSFER_FEES` | REVENUE | USD | Service fees collected on user transactions |
| `5001` | `EXPENSE_GATEWAY_FEES` | EXPENSE | USD | Processing fees charged by third-party processors |

---

## 3. Double-Entry Posting Rules

1. Every financial transaction must generate **two or more** `ledger_entries`.
2. The total sum of all debits must equal the total sum of all credits in that transaction:
   $$\sum \text{Debit Amounts} = \sum \text{Credit Amounts}$$
3. An entry must reference a valid `transaction_id` and a valid `account_id`.
4. Amounts must always be positive numbers (`amount > 0`).

---

## 4. Standard Transaction Scenarios

### Scenario A: User Deposits $100 via Payment Gateway
- User pays $100 through Stripe.
- Stripe takes a $2.90 processing fee.
- Platform credits user wallet with $100.

#### Journal Entry:
| Entry # | Account | Classification | Entry Type | Amount |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `GATEWAY_RECEIVABLE_STRIPE` | ASSET | **DEBIT** | $97.10 |
| 2 | `EXPENSE_GATEWAY_FEES` | EXPENSE | **DEBIT** | $2.90 |
| 3 | `USER_WALLET (User 123)` | LIABILITY | **CREDIT** | $100.00 |

*Check: Debits ($97.10 + $2.90 = $100.00) == Credits ($100.00).*

---

### Scenario B: P2P Transfer of $50 with a $1.00 Platform Fee
- User A sends $50 to User B.
- Platform charges User A a $1.00 fee.
- User A total deducted: $51.00.
- User B receives: $50.00.
- Platform earns: $1.00 revenue.

#### Journal Entry:
| Entry # | Account | Classification | Entry Type | Amount |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `USER_WALLET (User A)` | LIABILITY | **DEBIT** | $51.00 |
| 2 | `USER_WALLET (User B)` | LIABILITY | **CREDIT** | $50.00 |
| 3 | `REVENUE_TRANSFER_FEES` | REVENUE | **CREDIT** | $1.00 |

*Check: Debits ($51.00) == Credits ($50.00 + $1.00 = $51.00).*

---

### Scenario C: User Withdraws $40 to External Bank Account
- User initiates a $40 payout.

#### Step 1: Place on Hold (Escrow)
| Entry # | Account | Classification | Entry Type | Amount |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `USER_WALLET (User A)` | LIABILITY | **DEBIT** | $40.00 |
| 2 | `PENDING_SETTLEMENTS` | LIABILITY | **CREDIT** | $40.00 |

#### Step 2: Bank Webhook Confirms Success
| Entry # | Account | Classification | Entry Type | Amount |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `PENDING_SETTLEMENTS` | LIABILITY | **DEBIT** | $40.00 |
| 2 | `CASH_BANK_MAIN` | ASSET | **CREDIT** | $40.00 |

#### Alternative Step 2: Bank Webhook Reports Payout Failure (Rollback)
| Entry # | Account | Classification | Entry Type | Amount |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `PENDING_SETTLEMENTS` | LIABILITY | **DEBIT** | $40.00 |
| 2 | `USER_WALLET (User A)` | LIABILITY | **CREDIT** | $40.00 |

---

## 5. Balance Derivation Query

User wallet balances are calculated on-the-fly or cached materialized views derived from the ledger entries.

```sql
SELECT 
    a.id AS account_id,
    a.currency,
    -- For LIABILITY accounts (e.g. user wallets), Credits increase balance, Debits decrease balance
    COALESCE(SUM(
        CASE 
            WHEN le.entry_type = 'CREDIT' THEN le.amount
            WHEN le.entry_type = 'DEBIT' THEN -le.amount
            ELSE 0 
        END
    ), 0) AS current_balance
FROM accounts a
LEFT JOIN ledger_entries le ON a.id = le.account_id
WHERE a.id = :accountId
GROUP BY a.id, a.currency;
```

---

## 6. Ledger Invariant Integrity Check (Audit Script)

Run this scheduled health check query hourly:

```sql
-- Returns rows only if any transaction is unbalanced (CRITICAL ALERT)
SELECT 
    t.id AS transaction_id,
    t.reference,
    SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) AS total_debit,
    SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) AS total_credit
FROM transactions t
JOIN ledger_entries le ON t.id = le.transaction_id
GROUP BY t.id, t.reference
HAVING SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) != 
       SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END);
```

