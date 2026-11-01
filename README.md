# Fintech Platform (Ledger, Payment Engine & Next.js Portal)

[![Java](https://img.shields.io/badge/Java-17%20%2F%2021%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14.2%20(App%20Router)-black.svg)](https://nextjs.org/)
[![React](https://img.shields.io/badge/React-18.3-61DAFB.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-blue.svg)](https://www.typescriptlang.org/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-3.4-38B2AC.svg)](https://tailwindcss.com/)
[![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL%2015+-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

A production-grade, enterprise fintech platform featuring an ACID **Java / Spring Boot 3** double-entry ledger backend paired with a modern, responsive **Next.js 14** web application. This platform implements mission-critical financial engineering patterns: **Double-Entry Bookkeeping**, **Strict Idempotency**, **Pessimistic/Distributed Concurrency Controls**, **Cryptographically Verified Webhook Ingestion**, and an **Intuitive Customer & Admin Financial Portal**.

---

## Table of Contents
- [System Architecture](#system-architecture)
- [Frontend Portal Features](#frontend-portal-features)
- [Core Engineering Principles](#core-engineering-principles)
- [Transaction Flows](#transaction-flows)
- [Database Schema (ERD)](#database-schema-erd)
- [Tech Stack](#tech-stack)
- [Documentation Index](#documentation-index)
- [Project Directory Structure](#project-directory-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [1. Backend Setup](#1-backend-setup)
  - [2. Frontend Setup](#2-frontend-setup)
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

### Backend
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

### Frontend
| Domain | Technology | Justification |
| :--- | :--- | :--- |
| **Framework** | Next.js 14 (App Router) | Server-side rendering, file-based routing, SEO, and fast load times |
| **Library** | React 18 | Component-driven architecture and state management |
| **Language** | TypeScript 5 | Strict static type safety matching backend DTO schemas |
| **Styling** | Tailwind CSS 3.4 | Utility-first, enterprise-grade responsive UI styling |
| **HTTP Client**| Axios | Centralized JWT and automated `Idempotency-Key` interceptors |
| **Icons** | Lucide React | Clean, accessible financial UI icons |

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

```
java fintech/
├── frontend/                          # Next.js 14 Frontend Application
│   ├── src/
│   │   ├── app/                       # App Router routes & layouts
│   │   │   ├── page.tsx               # Auth (Login / Register)
│   │   │   ├── dashboard/page.tsx     # Wallet overview & balances
│   │   │   ├── transfers/page.tsx     # P2P Transfers
│   │   │   ├── payouts/page.tsx       # Bank Payouts
│   │   │   ├── kyc/page.tsx           # KYC verification & document upload
│   │   │   ├── admin/page.tsx         # Reconciliation trigger & audits
│   │   │   └── layout.tsx             # Root layout & providers
│   │   ├── components/                # UI components (Navbar, etc.)
│   │   ├── context/                   # Auth & session context
│   │   ├── lib/                       # Axios client & API endpoints
│   │   └── types/                     # TypeScript models & response DTOs
│   ├── package.json
│   └── tailwind.config.ts
│
├── src/                               # Spring Boot 3 Backend
│   ├── main/
│   │   ├── java/com/fintech/engine/
│   │   │   ├── common/                # Idempotency, GlobalExceptionHandler
│   │   │   ├── config/                # SecurityConfig, RedisConfig, CORS
│   │   │   ├── controller/            # REST Controllers (Auth, Transfer, etc.)
│   │   │   ├── dto/                   # Request/Response payloads
│   │   │   ├── entity/                # JPA entities (Account, LedgerEntry, etc.)
│   │   │   ├── repository/            # Spring Data JPA repositories with locks
│   │   │   ├── security/              # JWT filter, CustomUserDetailsService
│   │   │   └── service/               # Core double-entry ledger & transfers
│   │   └── resources/
│   │       ├── db/migration/          # Flyway SQL migrations
│   │       └── application.yml        # Spring environment configs
│   └── test/                          # Unit & integration tests
│
├── docker-compose.yml                 # PostgreSQL & Redis infrastructure
└── pom.xml                            # Maven dependencies
```

---

## Getting Started

### Prerequisites
- **JDK 17 or 21**
- **Node.js 18+ and npm**
- **Docker & Docker Compose** (for PostgreSQL and Redis)
- **Maven 3.8+** (or use the included `./mvnw`)

---

### 1. Backend Setup

1. **Clone repository**:
   ```bash
   git clone https://github.com/Collinsolayemi/fintech-payment-engine.git
   cd "java fintech"
   ```

2. **Start Infrastructure Services (PostgreSQL & Redis)**:
   ```bash
   docker compose up -d
   ```

3. **Build & Run Spring Boot Backend**:
   ```bash
   ./mvnw clean install
   ./mvnw spring-boot:run
   ```
   The backend API will be available at **`http://localhost:8080`**.

---

### 2. Frontend Setup

In a new terminal window:

1. **Navigate to frontend directory & install dependencies**:
   ```bash
   cd frontend
   npm install
   ```

2. **Configure Environment (Optional)**:
   Ensure `frontend/.env.local` points to your backend URL:
   ```env
   NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
   ```

3. **Start the Next.js Development Server**:
   ```bash
   npm run dev
   ```
   Open your browser at **[http://localhost:3000](http://localhost:3000)**.

---

### Default Roles & Access

- **Standard Customer**: Register a new user via the interface at `/` to automatically provision a multi-currency liability account.
- **Admin**: Users with the `ADMIN` role can navigate to `/admin` to trigger on-demand reconciliation and inspect the ledger consistency status.

---

## Production Readiness Checklist

- [ ] **Database Connection Pool**: Tuned HikariCP parameters for financial workloads.
- [ ] **Transaction Isolation**: Set to `READ_COMMITTED` with selective pessimistic locking (`PessimisticLock`).
- [ ] **Audit Logging**: MDC context logging with `userId`, `correlationId`, `clientIp`.
- [ ] **Secrets Management**: Credentials injected via environment variables or secret manager (HashiCorp Vault / AWS Secrets Manager).
- [ ] **Rate Limiting**: Token-bucket algorithm (Redis Bucket4j) to prevent brute-force and DDoS on sensitive routes.
- [ ] **Dead-Letter Queues (DLQ)**: Guaranteed message processing for failed webhook events.

