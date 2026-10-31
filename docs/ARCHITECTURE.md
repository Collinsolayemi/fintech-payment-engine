# Architecture & Technical Flows

This document details the core architectural mechanisms, concurrency controls, and transaction lifecycles of the Fintech backend.

---

## Table of Contents
1. [Core Design Concepts](#1-core-design-concepts)
2. [Concurrency & Race Condition Control](#2-concurrency--race-condition-control)
3. [Idempotency State Machine](#3-idempotency-state-machine)
4. [Transfer Flow (P2P)](#4-transfer-flow-p2p)
5. [Inbound Webhook Verification Flow](#5-inbound-webhook-verification-flow)
6. [Outbound Payout Flow](#6-outbound-payout-flow)
7. [Reconciliation Architecture](#7-reconciliation-architecture)

---

## 1. Core Design Concepts

### Invariant Preservation
In fintech systems, state transitions must never violate the fundamental invariant:
$$\sum_{\text{all accounts}} \Delta \text{Balance} = 0$$

All operations modify account balances solely through **balanced ledger journal entries**.

---

## 2. Concurrency & Race Condition Control

### The Double-Spending Threat
If a user with a balance of $100 issues two concurrent $100 transfer requests within 2 milliseconds:
- Request 1 checks balance: $100 >= $100 (Success)
- Request 2 checks balance: $100 >= $100 (Success)
- Both proceed to debit $\rightarrow$ Balance becomes -$100 (Fault).

### Locking Strategy
We use **Database Pessimistic Locking** (`SELECT ... FOR UPDATE`) over optimistic locking for financial balances. Optimistic locking causes excessive rollback retries under high contention.

```java
// Spring Data JPA Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.id = :id")
Optional<Account> findByIdWithLock(@Param("id") UUID id);
```

### Deadlock Prevention (Ordered Locking)
If User A sends money to User B at the same moment User B sends money to User A:
- Tx 1 locks Account A, waits for Account B.
- Tx 2 locks Account B, waits for Account A.
- **Deadlock occurs.**

**Solution:** Always acquire locks in deterministic alphabetical order of their UUID primary keys:

```java
UUID firstLockId = senderId.compareTo(recipientId) < 0 ? senderId : recipientId;
UUID secondLockId = senderId.compareTo(recipientId) < 0 ? recipientId : senderId;

Account firstAccount = accountRepository.findByIdWithLock(firstLockId)
    .orElseThrow(() -> new AccountNotFoundException(firstLockId));
Account secondAccount = accountRepository.findByIdWithLock(secondLockId)
    .orElseThrow(() -> new AccountNotFoundException(secondLockId));
```

---

## 3. Idempotency State Machine

Every mutating financial request carries an `Idempotency-Key` header.

```
       [ Client Request + Idempotency-Key ]
                         │
                         ▼
             [ Check Key in Redis/DB ]
                         │
         ┌───────────────┴───────────────┐
         │                               │
    [ Key Exists? ]                [ Key New? ]
         │                               │
    ┌────┴────────────┐                  ▼
    │                 │          [ Insert Key with ]
[ STATUS: IN_FLIGHT ] [ STATUS: COMPLETED ] [ STATUS = IN_FLIGHT ]
    │                 │                  │
    ▼                 ▼                  ▼
[ 409 Conflict / ] [ Return Cached  ] [ Process Financial ]
[ Wait with Lock ] [ Response Body  ] [ Transaction       ]
                                         │
                                         ▼
                                  [ Update Key with ]
                                  [ STATUS = COMPLETED + Resp Payload ]
```

### Key Storage Schema
```sql
CREATE TABLE idempotency_records (
    key VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL, -- SHA-256 of endpoint + body
    status VARCHAR(32) NOT NULL,       -- IN_FLIGHT, COMPLETED, FAILED
    response_code INT,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

---

## 4. Transfer Flow (P2P)

```
Client                     TransferService              LedgerService            Database
  │                               │                          │                      │
  │ 1. POST /api/v1/transfers     │                          │                      │
  │    (sender, recipient, amt)   │                          │                      │
  ├──────────────────────────────►│                          │                      │
  │                               │ 2. Check Idempotency     │                      │
  │                               │ 3. Sort IDs & Lock       │                      │
  │                               ├──────────────────────────┼─────────────────────►│ SELECT ... FOR UPDATE
  │                               │                          │                      │ (sender & recipient)
  │                               │ 4. Validate Balance      │                      │
  │                               │    (available >= amt)    │                      │
  │                               │                          │                      │
  │                               │ 5. Record Transaction    │                      │
  │                               ├─────────────────────────►│                      │
  │                               │                          │ 6. Create Journal    │
  │                               │                          │    Debit: Sender     │
  │                               │                          │    Credit: Recipient │
  │                               │                          ├─────────────────────►│ INSERT ledger_entries
  │                               │                          │                      │ (2 balanced rows)
  │                               │ 7. Mark Tx SUCCESS       │                      │
  │                               │ 8. Cache Idempotency     │                      │
  │ 9. 200 OK (TransferReceipt)   │                          │                      │
  │◄──────────────────────────────┤                          │                      │
```

---

## 5. Inbound Webhook Verification Flow

Payment gateways (Stripe, Paystack, Flutterwave) deliver asynchronous notifications for deposit events via HTTP POST.

```
Payment Gateway Provider       WebhookController            PaymentService            Database
          │                            │                          │                      │
          │ 1. POST /webhooks/{provider}                          │                      │
          │    Headers: X-Signature    │                          │                      │
          │    Body: {event, data}     │                          │                      │
          ├───────────────────────────►│                          │                      │
          │                            │ 2. Read Raw Payload Bytes│                      │
          │                            │ 3. Compute HMAC-SHA256   │                      │
          │                            │    (rawBytes, secretKey) │                      │
          │                            │ 4. Constant-Time Compare │                      │
          │                            │    (prevents timing attack)                     │
          │                            │                          │                      │
          │                            │ 5. Ingest Event          │                      │
          │                            ├─────────────────────────►│                      │
          │                            │                          │ 6. Check Event ID    │
          │                            │                          │    (De-duplication)  │
          │                            │                          │ 7. Credit User Wallet│
          │                            │                          │    Debit Gateway Asset
          │                            │                          ├─────────────────────►│ Commit Ledger
          │ 8. 200 OK (Acknowledged)   │                          │                      │
          │◄───────────────────────────┤                          │                      │
```

### Signature Verification Implementation (Java)
```java
public boolean verifyHmacSha256(byte[] payload, String receivedSignature, String secretKey) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] expectedHash = mac.doFinal(payload);
        String expectedSignature = HexFormat.of().formatHex(expectedHash);
        
        // Use constant-time equality check to prevent side-channel timing attacks
        return MessageDigest.isEqual(
            receivedSignature.getBytes(StandardCharsets.UTF_8),
            expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    } catch (Exception e) {
        log.error("Failed to verify webhook signature", e);
        return false;
    }
}
```

---

## 6. Outbound Payout Flow

When a user withdraws money from the platform to an external bank account:

1. **Funds Reservation (Escrow)**:
   - Debit: User Wallet Account (Liability)
   - Credit: Pending Settlements Account (Liability)
   - Status: `PENDING_EXTERNAL_PAYOUT`
2. **External Dispatch**:
   - Backend calls external payout API with external idempotency reference.
3. **Completion**:
   - Webhook returns `TRANSFER_SUCCESS`:
     - Debit: Pending Settlements Account
     - Credit: Platform Clearing Bank Account (Asset)
     - Status: `COMPLETED`
4. **Failure & Reversal**:
   - Webhook returns `TRANSFER_FAILED`:
     - Debit: Pending Settlements Account
     - Credit: User Wallet Account (Refund)
     - Status: `REVERSED`

---

## 7. Reconciliation Architecture

Reconciliation is the automated process that verifies that platform internal books match external financial realities.

### Daily Verification Matrix:
1. **Internal Trial Balance Integrity Check**:
   $$\sum \text{All Debits} - \sum \text{All Credits} = 0$$
   *Frequency: Every hour.*
2. **Gateway Settlement Reconciliation**:
   - Download provider settlement CSV / call settlement API.
   - Match provider transaction references against local database transaction rows.
   - Detect:
     - Missing transactions (present in bank, missing in ledger).
     - Ghost transactions (marked successful in ledger, missing in bank).
     - Fee discrepancies.

