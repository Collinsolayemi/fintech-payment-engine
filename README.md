# Fintech Backend Core (Ledger, Wallets & Payment Engine)

[![Java](https://img.shields.io/badge/Java-17%20%2F%2021%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL%2015+-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

A production-grade, enterprise fintech backend blueprint built with **Java** and **Spring Boot 3**. This system implements mission-critical financial engineering patterns: **Double-Entry Bookkeeping**, **Strict Idempotency**, **Pessimistic/Distributed Concurrency Controls**, and **Cryptographically Verified Webhook Ingestion**.

---

## Table of Contents
- [System Architecture](#system-architecture)
- [Core Engineering Principles](#core-engineering-principles)
- [Transaction Flows](#transaction-flows)
- [Database Schema (ERD)](#database-schema-erd)
- [Tech Stack](#tech-stack)
- [Documentation Index](#documentation-index)
- [Project Directory Structure](#project-directory-structure)
- [Getting Started](#getting-started)
- [Production Readiness Checklist](#production-readiness-checklist)

---

## System Architecture

```
                          [ Client Applications ]
                       (Web / iOS / Android / POS)
                                   │
                                   ▼
                      [ TLS 1.3 / API Gateway ]
                      (Rate Limiting & WAF)
                                   │
                                   ▼
                   ┌───────────────────────────────┐
                   │     Spring Boot 3 Backend     │
                   │                               │
                   │  ┌─────────────────────────┐  │
                   │  │ Spring Security & JWT   │  │
                   │  └───────────┬─────────────┘  │
                   │              │                │
                   │  ┌───────────▼─────────────┐  │
                   │  │  Idempotency Filter     │  │
                   │  └───────────┬─────────────┘  │
                   │              │                │
                   │  ┌───────────▼─────────────┐  │
                   │  │  Wallet & Transfer Core │  │
                   │  └─────┬──────────────┬────┘  │
                   └────────┼──────────────┼───────┘
                            │              │
              ┌─────────────▼────┐    ┌────▼─────────────┐
              │ PostgreSQL 15+   │    │ Redis Cluster    │
              │ (ACID Ledger)    │    │ (Locks & Cache)  │
              └──────────────────┘    └──────────────────┘
                       ▲
                       │
       ┌───────────────┴────────────────┐
       │ Scheduled Reconciliation Engine│
       │ (Daily Check: ΣDebits == ΣCredits)
       └────────────────────────────────┘
```

---

## Core Engineering Principles

### 1. Double-Entry Bookkeeping
Money is never created, destroyed, or updated in place. Every transaction consists of balanced journal entries:
$$\sum \text{Debits} = \sum \text{Credits}$$
- **Asset & Expense accounts** increase with Debits and decrease with Credits.
- **Liability, Equity, and Revenue accounts** increase with Credits and decrease with Debits.
- Balances are derived from immutable historical ledger postings.

### 2. Strict Idempotency
All mutating financial endpoints (`POST /api/v1/transfers`, `POST /api/v1/payments`) require a unique `Idempotency-Key` (UUIDv4) header:
- In-flight requests lock the key in Redis or the database.
- Completed operations return the cached response payload for duplicate requests.
- Prevents double-charges caused by network drops or client retries.

### 3. Concurrency & Race-Condition Prevention
To eliminate double-spending:
- Accounts are locked during state transitions using database pessimistic write locks (`SELECT ... FOR UPDATE` with `NOWAIT` or bounded timeouts).
- Account updates follow deterministic ordering (e.g., sort account IDs lexicographically before acquiring locks) to prevent database deadlocks.

### 4. Monetary Precision
- Floating-point types (`float`, `double`) are strictly prohibited to avoid binary rounding errors.
- All calculations use **`BigDecimal`** (with explicit rounding modes) or are stored as integers in the currency's minor unit (e.g., cents, pence, kobo: `$100.50` = `10050`).

### 5. Append-Only Immutability
- Financial rows (`ledger_entries`, `transactions`) are strictly **APPEND-ONLY**.
- No `UPDATE` or `DELETE` SQL operations are permitted on financial tables.
- Erroneous transactions are resolved through explicit offsetting/reversal transactions.

---

## Transaction Flows

### 1. Peer-to-Peer (P2P) Internal Transfer
```
User A (Sender)            Fintech Backend               Database Ledger
     │                            │                             │
     │ 1. POST /transfers         │                             │
     │    (Idempotency-Key)       │                             │
     ├───────────────────────────►│                             │
     │                            │ 2. Check Idempotency Key    │
     │                            │ 3. Lock Accounts (A & B)    │
     │                            ├────────────────────────────►│ SELECT FOR UPDATE
     │                            │ 4. Verify Sender Balance    │
     │                            │ 5. Create Transaction Row   │
     │                            │ 6. Write Debit Entry (A)    │
     │                            │ 7. Write Credit Entry (B)   │
     │                            │ 8. Commit Transaction       │
     │                            ├────────────────────────────►│ COMMIT
     │ 9. 200 OK (Transfer Resp)  │                             │
     │◄───────────────────────────┤                             │
```

### 2. Inbound Deposit (Webhook Flow)
```
Payment Provider (Stripe/Paystack)     Fintech Backend         Database Ledger
               │                              │                       │
               │ 1. POST /webhooks/gateway    │                       │
               │    (Payload + HMAC Header)   │                       │
               ├─────────────────────────────►│                       │
               │                              │ 2. Verify HMAC SHA256 │
               │                              │ 3. Check Event Idem.  │
               │                              │ 4. Post Ledger Entry  │
               │                              │    (Debit: Gateway)   │
               │                              │    (Credit: User)     │
               │                              ├──────────────────────►│ COMMIT
               │ 5. 200 OK (Acknowledged)     │                       │
               │◄─────────────────────────────┤                       │
```

---

## Database Schema (ERD)

```
┌──────────────────┐       1:N       ┌──────────────────┐
│      users       ├────────────────►│     accounts     │
├──────────────────┤                 ├──────────────────┤
│ id (UUID, PK)    │                 │ id (UUID, PK)    │
│ email (VARCHAR)  │                 │ user_id (FK)     │
│ kyc_tier (INT)   │                 │ currency (ISO)   │
│ created_at       │                 │ type (LIABILITY) │
└──────────────────┘                 │ status (ACTIVE)  │
                                     └────────┬─────────┘
                                              │ 1:N
                                              ▼
┌──────────────────┐       1:N       ┌──────────────────┐
│   transactions   ├────────────────►│  ledger_entries  │
├──────────────────┤                 ├──────────────────┤
│ id (UUID, PK)    │                 │ id (BIGSERIAL)   │
│ reference (UK)   │                 │ transaction_id   │
│ idempotency_key  │                 │ account_id (FK)  │
│ status           │                 │ entry_type (D/C) │
│ description      │                 │ amount (NUMERIC) │
│ created_at       │                 │ created_at       │
└──────────────────┘                 └──────────────────┘
```

---

## Tech Stack

| Domain | Technology | Justification |
| :--- | :--- | :--- |
| **Language** | Java 17 / 21 LTS | Strong typing, virtual threads, high ecosystem maturity |
| **Framework** | Spring Boot 3.x | Industry standard for enterprise microservices |
| **Database** | PostgreSQL 15+ | Reliable ACID guarantees, pessimistic locks, JSONB |
| **Migrations** | Flyway | Strict, reproducible, versioned schema management |
| **Cache & Locks**| Redis + Redisson | Distributed locks, rate limiting, and idempotency store |
| **Messaging** | RabbitMQ / Kafka | Reliable async webhook processing & event notifications |
| **Security** | Spring Security + JWT | Stateless token authentication, PBKDF2/BCrypt hashing |
| **Testing** | JUnit 5 + Testcontainers | Integration tests against real PostgreSQL & Redis instances |

---

## Documentation Index

Detailed architectural and design documentation is available in the [`docs/`](docs/) directory:

1. [**System Architecture & Technical Flows (`docs/ARCHITECTURE.md`)**](docs/ARCHITECTURE.md)
   - Deep dive into P2P transfers, deadlock prevention, locking strategies, and webhook security.
2. [**Double-Entry Ledger Specification (`docs/LEDGER_SPECIFICATION.md`)**](docs/LEDGER_SPECIFICATION.md)
   - Chart of Accounts, Debit/Credit rules, sample journal entries, balance derivations, and reconciliation rules.
3. [**API Reference Specification (`docs/API_REFERENCE.md`)**](docs/API_REFERENCE.md)
   - Complete endpoint contracts, request/response payloads, HTTP status codes, and error models.

---

## Project Directory Structure

Standard recommended package hierarchy for Spring Boot:

```
src/
├── main/
│   ├── java/com/fintech/core/
│   │   ├── common/
│   │   │   ├── exceptions/          # Business exceptions & GlobalExceptionHandler
│   │   │   ├── idempotency/         # Idempotency filter, annotations, repository
│   │   │   └── util/                # Money formatting, reference generators
│   │   ├── config/                  # Security, Redis, Async, OpenAPI configs
│   │   ├── modules/
│   │   │   ├── auth/                # JWT auth, user registration, 2FA
│   │   │   ├── user/                # User profile, KYC status
│   │   │   ├── account/             # Wallet/account management
│   │   │   ├── ledger/              # Core double-entry ledger & entry postings
│   │   │   ├── transfer/            # P2P transfers & transfer validation
│   │   │   ├── payment/             # External payment gateway & webhook ingestion
│   │   │   └── reconciliation/      # Daily automated integrity check jobs
│   │   └── Application.java         # Main Spring Boot entrypoint
│   └── resources/
│       ├── db/migration/            # Flyway SQL migrations (V1__init.sql, etc.)
│       └── application.yml          # Spring configuration
└── test/
    └── java/com/fintech/core/       # Unit & Testcontainers integration tests
```

---

## Getting Started

### Prerequisites
- **JDK 17 or 21**
- **Docker & Docker Compose** (for PostgreSQL and Redis)
- **Maven 3.8+**

### Local Setup
1. **Clone repository**:
   ```bash
   git clone https://github.com/your-org/fintech-backend.git
   cd "java fintech"
   ```

2. **Start Infrastructure Services**:
   ```bash
   docker compose up -d
   ```

3. **Run Migrations & Build**:
   ```bash
   ./mvnw clean install
   ```

4. **Launch Application**:
   ```bash
   ./mvnw spring-boot:run
   ```

---

## Production Readiness Checklist

- [ ] **Database Connection Pool**: Tuned HikariCP parameters for financial workloads.
- [ ] **Transaction Isolation**: Set to `READ_COMMITTED` with selective pessimistic locking (`PessimisticLock`).
- [ ] **Audit Logging**: MDC context logging with `userId`, `correlationId`, `clientIp`.
- [ ] **Secrets Management**: Credentials injected via environment variables or secret manager (HashiCorp Vault / AWS Secrets Manager).
- [ ] **Rate Limiting**: Token-bucket algorithm (Redis Bucket4j) to prevent brute-force and DDoS on sensitive routes.
- [ ] **Dead-Letter Queues (DLQ)**: Guaranteed message processing for failed webhook events.

