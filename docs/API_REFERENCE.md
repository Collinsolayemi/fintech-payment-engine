# API Reference Specification

Base URL: `/api/v1`

All mutating endpoints require an `Idempotency-Key` header with a valid UUIDv4.

---

## 1. Authentication & Security Endpoints

### Register Customer
`POST /api/v1/auth/register`

Creates customer credentials, hashes password with BCrypt, automatically provisions their default wallet account, and returns both JWT access token and refresh token.

#### Request:
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123!",
  "firstName": "Alex",
  "lastName": "Morgan",
  "currency": "USD"
}
```

#### Response: `201 Created`
```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "7c4a1234-abcd-ef01-2345-6789abcdef01",
  "expiresInSeconds": 86400,
  "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "email": "user@example.com",
  "role": "ROLE_USER",
  "defaultAccountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "defaultAccountNumber": "USD-11223344"
}
```

---

### Authenticate (Login)
`POST /api/v1/auth/login`

Authenticates credentials and issues signed JWT Bearer token + refresh token.

#### Request:
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123!"
}
```

#### Response: `200 OK`
```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "7c4a1234-abcd-ef01-2345-6789abcdef01",
  "expiresInSeconds": 86400,
  "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "email": "user@example.com",
  "role": "ROLE_USER",
  "defaultAccountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "defaultAccountNumber": "USD-11223344"
}
```

---

### Forgot Password (Request OTP)
`POST /api/v1/auth/forgot-password`

Dispatches a secure 6-digit numeric OTP to the user's email with a 60-second cooldown and 10-minute expiry.

#### Request:
```json
{
  "email": "user@example.com"
}
```

#### Response: `200 OK`
```json
{
  "message": "OTP verification code sent successfully to user@example.com",
  "email": "user@example.com",
  "purpose": "FORGOT_PASSWORD",
  "expiresInSeconds": 600
}
```

---

### Verify OTP
`POST /api/v1/auth/verify-otp`

Validates the OTP code using constant-time comparison, limits attempts to 5 max, and returns a verified reset token.

#### Request:
```json
{
  "email": "user@example.com",
  "otpCode": "481920",
  "purpose": "FORGOT_PASSWORD"
}
```

#### Response: `200 OK`
```json
{
  "message": "OTP verified successfully",
  "email": "user@example.com",
  "purpose": "FORGOT_PASSWORD",
  "expiresInSeconds": 0,
  "resetToken": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

---

### Reset Password
`POST /api/v1/auth/reset-password`

Consumes the verified reset token, updates password hash, and terminates all active sessions across devices.

#### Request:
```json
{
  "email": "user@example.com",
  "resetToken": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "newPassword": "NewSecurePassword456!"
}
```

#### Response: `200 OK`
```json
{
  "success": true,
  "message": "Password has been reset successfully. Please log in with your new password."
}
```

---

### Resend OTP
`POST /api/v1/auth/resend-otp`

Dispatches a fresh OTP code respecting rate limiting cooldown.

#### Request:
```json
{
  "email": "user@example.com",
  "purpose": "FORGOT_PASSWORD"
}
```

#### Response: `200 OK`
```json
{
  "message": "OTP verification code sent successfully to user@example.com",
  "email": "user@example.com",
  "purpose": "FORGOT_PASSWORD",
  "expiresInSeconds": 600
}
```

---

### Change Password
`POST /api/v1/auth/change-password`

Authenticated endpoint for password rotation. Verifies current password and invalidates other sessions.

#### Headers:
- `Authorization: Bearer <JWT>`

#### Request:
```json
{
  "currentPassword": "CurrentPassword123!",
  "newPassword": "BrandNewPassword789!"
}
```

#### Response: `200 OK`
```json
{
  "success": true,
  "message": "Password changed successfully. Existing sessions have been revoked."
}
```

---

### Refresh Access Token (Token Rotation)
`POST /api/v1/auth/refresh-token`

Rotates the refresh token (revoking the used token) and issues a fresh JWT access token.

#### Request:
```json
{
  "refreshToken": "7c4a1234-abcd-ef01-2345-6789abcdef01"
}
```

#### Response: `200 OK`
```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "9e12abcd-0123-4567-89ab-cdef01234567",
  "expiresInSeconds": 86400,
  "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "email": "user@example.com",
  "role": "ROLE_USER",
  "defaultAccountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "defaultAccountNumber": "USD-11223344"
}
```

---

### Logout
`POST /api/v1/auth/logout`

Revokes the refresh token and terminates the user session.

#### Request:
```json
{
  "refreshToken": "9e12abcd-0123-4567-89ab-cdef01234567"
}
```

#### Response: `200 OK`
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

---

## 2. Wallet & Account Endpoints

### Get Current Wallet Balance
`GET /api/v1/wallets/me`

#### Headers:
- `Authorization: Bearer <JWT>`

#### Response: `200 OK`
```json
{
  "accountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "currency": "USD",
  "availableBalance": 2450.50,
  "ledgerBalance": 2450.50,
  "status": "ACTIVE"
}
```

---

### Get Account Statement / Ledger History
`GET /api/v1/wallets/me/entries?page=0&size=20`

#### Headers:
- `Authorization: Bearer <JWT>`

#### Response: `200 OK`
```json
{
  "content": [
    {
      "entryId": 10452,
      "transactionReference": "TX-9428-A21F",
      "entryType": "CREDIT",
      "amount": 100.00,
      "description": "P2P transfer from john@example.com",
      "createdAt": "2026-10-31T14:15:00Z"
    },
    {
      "entryId": 10450,
      "transactionReference": "TX-9411-B10E",
      "entryType": "DEBIT",
      "amount": 25.00,
      "description": "Merchant checkout payment",
      "createdAt": "2026-10-31T12:00:00Z"
    }
  ],
  "totalElements": 2,
  "totalPages": 1
}
```

---

## 3. Transaction & Transfer Endpoints

### Initiate P2P Transfer
`POST /api/v1/transfers`

#### Headers:
- `Authorization: Bearer <JWT>`
- `Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7`

#### Request:
```json
{
  "recipientAccountId": "e4a2c510-1891-4560-9dfa-8a1a9e3e7f41",
  "amount": 50.00,
  "currency": "USD",
  "description": "Lunch split payment",
  "pin": "1234"
}
```

#### Response: `200 OK`
```json
{
  "transactionReference": "TX-20261031-7C9E",
  "status": "COMPLETED",
  "amount": 50.00,
  "fee": 0.00,
  "currency": "USD",
  "senderAccountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "recipientAccountId": "e4a2c510-1891-4560-9dfa-8a1a9e3e7f41",
  "timestamp": "2026-10-31T15:35:10Z"
}
```

---

## 4. Payment Gateway Webhook Endpoints

### Ingest Inbound Payment Webhook
`POST /api/v1/webhooks/{provider}`

Cryptographically verifies inbound webhook notifications (e.g. Stripe, Paystack, Flutterwave) via HMAC-SHA256, prevents replay attacks via event de-duplication, and auto-credits the destination customer wallet via balanced double-entry ledger entries.

#### Path Parameters:
- `provider`: `STRIPE`, `PAYSTACK`, or `FLUTTERWAVE`

#### Headers:
- `X-Signature` or `Stripe-Signature`: HMAC-SHA256 signature hash

#### Request Body:
```json
{
  "id": "evt_3N4abc123456789",
  "type": "payment.succeeded",
  "data": {
    "amount": "250.00",
    "currency": "USD",
    "customer_email": "customer@example.com"
  }
}
```

#### Response: `200 OK`
```json
{
  "success": true,
  "message": "Payment webhook processed and funds credited",
  "eventId": "evt_3N4abc123456789",
  "status": "PROCESSED"
}
```

---

## 5. Outbound Payout (Withdrawal) Endpoints

### Initiate Payout (Phase 1 Escrow Lock)
`POST /api/v1/payouts`

Debits user available balance, moves funds into platform pending escrow (`SYS-2002-USD`), debits fee (`SYS-4001-USD`), and issues a payout reference.

#### Headers:
- `Authorization: Bearer <JWT>`
- `Idempotency-Key: <UUIDv4>` (Optional, recommended)

#### Request:
```json
{
  "accountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "amount": 100.00,
  "currency": "USD",
  "destinationBankCode": "044",
  "destinationAccountNumber": "0123456789",
  "destinationAccountName": "Alex Morgan",
  "description": "Withdrawal to Chase checking"
}
```

#### Response: `200 OK`
```json
{
  "id": "b18a8b1b-21a4-4df1-863a-bc49048a1234",
  "reference": "PO-20261031-102938",
  "accountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "accountNumber": "USD-11223344",
  "amount": 100.0000,
  "fee": 2.0000,
  "currency": "USD",
  "status": "PROCESSING",
  "destinationBankCode": "044",
  "destinationAccountNumber": "0123456789",
  "destinationAccountName": "Alex Morgan",
  "failureReason": null,
  "createdAt": "2026-10-31T22:15:00Z",
  "updatedAt": "2026-10-31T22:15:00Z"
}
```

---

### Get Payout Details
`GET /api/v1/payouts/{reference}`

#### Headers:
- `Authorization: Bearer <JWT>`

#### Response: `200 OK`

---

### List Current User Payouts
`GET /api/v1/payouts/me`

#### Headers:
- `Authorization: Bearer <JWT>`

#### Response: `200 OK` (Array of PayoutResponse)

---

### Complete Payout Settlement (Phase 2A External Settlement)
`POST /api/v1/payouts/{reference}/settle`

Posts settlement journal entry from Escrow (`SYS-2002-USD`) to Clearing Bank (`SYS-1001-USD`) and marks payout `COMPLETED`.

#### Response: `200 OK`

---

### Reverse Payout Settlement (Phase 2B External Reversal)
`POST /api/v1/payouts/{reference}/reverse`

Releases funds from Escrow (`SYS-2002-USD`) and refunds full principal + fee back into the customer's wallet. Marks payout `REVERSED`.

#### Request:
```json
{
  "success": false,
  "failureReason": "Account closed or invalid account number"
}
```

#### Response: `200 OK`

---

## 6. Reconciliation & Audit Engine

Admin-only endpoints (`ROLE_ADMIN`) for scheduled and on-demand trial balance audits, invariant checks, and payment gateway settlement matching.

### Run Internal Integrity Audit
Runs the automated internal ledger check: global trial balance ($\sum \text{Debits} == \sum \text{Credits}$), currency trial balance, per-transaction balance, negative wallet checks, escrow alignment, and posting completeness.

`POST /api/v1/reconciliation/runs`

#### Headers:
- `Authorization: Bearer <ADMIN_JWT_TOKEN>`

#### Response: `201 Created`
```json
{
  "id": "e58b8f21-7d12-4f36-9b81-a537f2a10b42",
  "type": "INTERNAL_INTEGRITY",
  "status": "PASSED",
  "provider": null,
  "triggeredBy": "admin@fintech.com",
  "totalDebits": 1500000.0000,
  "totalCredits": 1500000.0000,
  "discrepancyCount": 0,
  "startedAt": "2026-11-01T21:00:00Z",
  "completedAt": "2026-11-01T21:00:02Z",
  "discrepancies": []
}
```

---

### Reconcile Gateway Settlement
Matches an external provider settlement report against ledger deposit records (`DEP-{PROVIDER}-{reference}`) within a specified period.

`POST /api/v1/reconciliation/settlements/{provider}`

Path variable `provider`: `STRIPE`, `PAYSTACK`, `FLUTTERWAVE`

#### Headers:
- `Authorization: Bearer <ADMIN_JWT_TOKEN>`
- `Content-Type: application/json`

#### Request:
```json
{
  "periodStart": "2026-10-31T00:00:00Z",
  "periodEnd": "2026-11-01T00:00:00Z",
  "records": [
    {
      "reference": "ch_3NabcXYZ123456",
      "amount": 250.00,
      "currency": "USD"
    }
  ]
}
```

#### Response: `201 Created`
```json
{
  "id": "f12c3d4e-5a6b-7c8d-9e0f-1a2b3c4d5e6f",
  "type": "GATEWAY_SETTLEMENT",
  "status": "FAILED",
  "provider": "STRIPE",
  "triggeredBy": "admin@fintech.com",
  "periodStart": "2026-10-31T00:00:00Z",
  "periodEnd": "2026-11-01T00:00:00Z",
  "externalTotal": 250.0000,
  "ledgerTotal": 150.0000,
  "recordsChecked": 1,
  "discrepancyCount": 1,
  "startedAt": "2026-11-01T21:05:00Z",
  "completedAt": "2026-11-01T21:05:01Z",
  "discrepancies": [
    {
      "id": "a9b8c7d6-e5f4-3210-fedc-ba9876543210",
      "type": "AMOUNT_MISMATCH",
      "severity": "CRITICAL",
      "reference": "ch_3NabcXYZ123456",
      "currency": "USD",
      "expectedAmount": 250.00,
      "actualAmount": 150.00,
      "details": "Provider settled 250.00 but ledger credited 150.00 for DEP-STRIPE-ch_3NabcXYZ123456",
      "createdAt": "2026-11-01T21:05:01Z"
    }
  ]
}
```

---

### List Reconciliation Runs
`GET /api/v1/reconciliation/runs?page=0&size=20`

#### Headers:
- `Authorization: Bearer <ADMIN_JWT_TOKEN>`

#### Response: `200 OK`

---

### Get Reconciliation Run Report
`GET /api/v1/reconciliation/runs/{runId}`

#### Headers:
- `Authorization: Bearer <ADMIN_JWT_TOKEN>`

#### Response: `200 OK`

---

## 7. User Profile & KYC Management

Endpoints for viewing customer profiles, inspecting KYC daily/single limits, requesting tier upgrades, and administrative adjudication.

### Get My Profile
Retrieves full user details, contact information, current KYC tier, and calculated daily remaining volume.

`GET /api/v1/users/me`

#### Headers:
- `Authorization: Bearer <USER_JWT_TOKEN>`

#### Response: `200 OK`
```json
{
  "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "email": "customer@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "role": "ROLE_USER",
  "kycTier": 1,
  "kycTierDescription": "Basic (Email Verified)",
  "singleTransactionLimit": 500.00,
  "dailyLimit": 1000.00,
  "dailyUsedToday": 150.00,
  "dailyRemainingToday": 850.00,
  "kycStatus": "APPROVED",
  "phoneNumber": "+1234567890",
  "addressLine": "100 Broadway",
  "city": "New York",
  "country": "USA",
  "postalCode": "10005",
  "dateOfBirth": "1992-05-14",
  "createdAt": "2026-10-31T12:00:00Z",
  "updatedAt": "2026-11-01T15:00:00Z"
}
```

---

### Update My Profile
Updates personal address and contact details.

`PATCH /api/v1/users/me`

#### Headers:
- `Authorization: Bearer <USER_JWT_TOKEN>`
- `Content-Type: application/json`

#### Request:
```json
{
  "firstName": "Jonathan",
  "phoneNumber": "+1234567890",
  "addressLine": "250 Fifth Avenue",
  "city": "New York",
  "country": "USA",
  "postalCode": "10001"
}
```

#### Response: `200 OK` (Updated UserProfileResponse)

---

### Submit KYC Upgrade Application
Submits identity documents to request promotion from Tier 1 to Tier 2 (or Tier 2 to Tier 3). Progression must be sequential.

`POST /api/v1/users/kyc/upgrade`

#### Headers:
- `Authorization: Bearer <USER_JWT_TOKEN>`
- `Content-Type: application/json`

#### Request:
```json
{
  "targetTier": 2,
  "idType": "PASSPORT",
  "idNumber": "A12345678",
  "documentUrl": "https://storage.fintech.example.com/kyc/doc-passport.jpg"
}
```

#### Response: `201 Created`
```json
{
  "id": "c7a8b9d0-1234-4567-89ab-cdef01234567",
  "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "userEmail": "customer@example.com",
  "targetTier": 2,
  "idType": "PASSPORT",
  "idNumber": "A12345678",
  "documentUrl": "https://storage.fintech.example.com/kyc/doc-passport.jpg",
  "status": "SUBMITTED",
  "rejectionReason": null,
  "reviewedBy": null,
  "createdAt": "2026-11-01T21:50:00Z",
  "updatedAt": "2026-11-01T21:50:00Z"
}
```

---

### List My KYC Applications
`GET /api/v1/users/kyc/applications`

#### Headers:
- `Authorization: Bearer <USER_JWT_TOKEN>`

#### Response: `200 OK` (Array of KycApplicationResponse)

---

### Admin: Search & List Customers
`GET /api/v1/admin/users?search=john&page=0&size=20`

#### Headers:
- `Authorization: Bearer <ADMIN_JWT_TOKEN>`

#### Response: `200 OK` (Spring Data Page of UserProfileResponse)

---

### Admin: Review KYC Application
Adjudicates an application. Approving the request immediately promotes the user to the requested KYC tier.

`POST /api/v1/admin/users/kyc/applications/{id}/review`

#### Headers:
- `Authorization: Bearer <ADMIN_JWT_TOKEN>`
- `Content-Type: application/json`

#### Request:
```json
{
  "status": "APPROVED",
  "rejectionReason": null
}
```

#### Response: `200 OK` (Updated KycApplicationResponse)

---

## 8. Transaction History & Ledger Statement Endpoints

### Direct Wallet Deposit / Top-up
`POST /api/v1/transactions/deposit`

Credits user liability account and debits the system clearing account (`SYS-1002-<CURRENCY>`), maintaining strict double-entry balance. Supports idempotency via `Idempotency-Key` header.

#### Headers:
- `Authorization: Bearer <JWT_TOKEN>`
- `Idempotency-Key: <UUIDv4>`
- `Content-Type: application/json`

#### Request:
```json
{
  "accountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "amount": 250.00,
  "currency": "USD",
  "description": "Direct wallet top-up"
}
```

#### Response: `201 Created`
```json
{
  "id": "e8e50bc9-3d19-4cb5-b5f7-f1396b797825",
  "reference": "DEP-20261104-582914",
  "accountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "accountNumber": "USD-11223344",
  "type": "DEPOSIT",
  "amount": 250.00,
  "currency": "USD",
  "description": "Direct wallet top-up",
  "status": "COMPLETED",
  "createdAt": "2026-11-04T08:30:00Z"
}
```

---

### Get Authenticated User Transactions
`GET /api/v1/transactions/me`

Returns user-scoped transaction summaries with signed amounts (credits positive, debits negative).

#### Headers:
- `Authorization: Bearer <JWT_TOKEN>`

#### Response: `200 OK`
```json
[
  {
    "id": "e8e50bc9-3d19-4cb5-b5f7-f1396b797825",
    "reference": "DEP-20261104-582914",
    "accountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
    "accountNumber": "USD-11223344",
    "type": "CREDIT",
    "amount": 250.00,
    "currency": "USD",
    "description": "Direct wallet top-up",
    "status": "COMPLETED",
    "createdAt": "2026-11-04T08:30:00Z"
  }
]
```

---

### Get Account Transactions
`GET /api/v1/transactions/account/{accountId}`

Returns ledger postings scoped to a specific wallet or clearing account.

---

### Get Transaction by Reference
`GET /api/v1/transactions/{reference}`

Retrieves transaction status and metadata by reference code (e.g. `DEP-...`, `TXN-...`, `PO-...`).

---

### Export CSV Statement
`GET /api/v1/transactions/account/{accountId}/statement?startDate=2026-01-01T00:00:00Z&endDate=2026-12-31T23:59:59Z`

Streams an audit-grade RFC 4180 CSV export with transaction date, reference, entry type, debit/credit amount, balance, and description.

#### Headers:
- `Authorization: Bearer <JWT_TOKEN>`

#### Response: `200 OK`
- `Content-Type: text/csv`
- `Content-Disposition: attachment; filename="statement-{accountId}.csv"`

---

## 9. Foreign Exchange (FX) & Multi-Currency Engine

### Provision Multi-Currency Wallet
`POST /api/v1/accounts/wallet`

Opens an additional currency wallet (EUR, GBP, CAD, AUD, JPY, NGN) for the authenticated customer.

#### Request:
```json
{
  "currency": "EUR",
  "accountName": "Euro Business Wallet"
}
```

#### Response: `201 Created`
```json
{
  "accountId": "7bf361c7-c533-4f96-b072-cb07817eb6a3",
  "accountNumber": "EUR-48201948",
  "accountName": "Euro Business Wallet",
  "accountType": "LIABILITY",
  "currency": "EUR",
  "status": "ACTIVE",
  "balance": 0.00,
  "totalDebits": 0.00,
  "totalCredits": 0.00
}
```

---

### Get Live FX Rates
`GET /api/v1/fx/rates?base=USD`

Retrieves interbank market reference rates and inverse conversion multipliers.

#### Response: `200 OK`
```json
[
  {
    "sourceCurrency": "USD",
    "targetCurrency": "EUR",
    "rate": 0.925000,
    "inverseRate": 1.081081,
    "timestamp": "2026-11-04T14:30:00Z"
  }
]
```

---

### Request Guaranteed FX Quote
`POST /api/v1/fx/quote`

Locks in a guaranteed conversion rate with an institutional spread for 60 seconds.

#### Request:
```json
{
  "sourceCurrency": "USD",
  "targetCurrency": "EUR",
  "sourceAmount": 100.00
}
```

#### Response: `201 Created`
```json
{
  "quoteId": "9a281f47-805f-48f3-9ddb-62cc5f865584",
  "sourceCurrency": "USD",
  "targetCurrency": "EUR",
  "sourceAmount": 100.00,
  "targetAmount": 92.18,
  "exchangeRate": 0.921763,
  "fee": 0.50,
  "expiresInSeconds": 60,
  "expiresAt": "2026-11-04T14:31:00Z"
}
```

---

### Execute Currency Exchange
`POST /api/v1/fx/execute`

Executes balanced double-entry conversion across multi-currency clearing accounts and user wallets.

#### Headers:
- `Authorization: Bearer <JWT_TOKEN>`
- `Idempotency-Key: <UUIDv4>`

#### Request:
```json
{
  "quoteId": "9a281f47-805f-48f3-9ddb-62cc5f865584",
  "sourceAccountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "targetAccountId": "7bf361c7-c533-4f96-b072-cb07817eb6a3"
}
```

#### Response: `200 OK`
```json
{
  "transactionReference": "FX-20261104-741296",
  "sourceAccountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "targetAccountId": "7bf361c7-c533-4f96-b072-cb07817eb6a3",
  "sourceAmount": 100.00,
  "targetAmount": 92.18,
  "sourceCurrency": "USD",
  "targetCurrency": "EUR",
  "exchangeRate": 0.921763,
  "fee": 0.50,
  "executedAt": "2026-11-04T14:30:15Z"
}
```

---

## 10. Virtual Cards Issuing & Management Engine

### Issue Virtual Debit Card
`POST /api/v1/cards/issue`

Provisions a virtual Visa or Mastercard debit card linked to any active user wallet.

#### Request:
```json
{
  "accountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "cardholderName": "Alex Morgan",
  "cardBrand": "VISA",
  "monthlyLimit": 5000.00
}
```

#### Response: `201 Created`
```json
{
  "id": "63c6e802-ea26-4d8f-84a9-1025740afc4b",
  "accountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12",
  "accountNumber": "USD-11223344",
  "currency": "USD",
  "cardholderName": "Alex Morgan",
  "maskedCardNumber": "•••• •••• •••• 4709",
  "lastFour": "4709",
  "expiryMonth": 11,
  "expiryYear": 2029,
  "status": "ACTIVE",
  "cardBrand": "VISA",
  "cardType": "VIRTUAL_DEBIT",
  "monthlyLimit": 5000.00,
  "onlinePaymentsEnabled": true,
  "internationalEnabled": true,
  "createdAt": "2026-11-04T14:30:00Z"
}
```

---

### Reveal Sensitive Card Details
`GET /api/v1/cards/{cardId}/details`

Securely reveals full 16-digit PAN and CVV. Requires authenticated cardholder session.

#### Response: `200 OK`
```json
{
  "id": "63c6e802-ea26-4d8f-84a9-1025740afc4b",
  "cardNumber": "4532890123454709",
  "cvv": "682",
  "expiryMonth": 11,
  "expiryYear": 2029,
  "cardholderName": "Alex Morgan"
}
```

---

### Update Card Freeze Status
`PATCH /api/v1/cards/{cardId}/status`

Instantly freezes or unfreezes card.

#### Request:
```json
{
  "status": "FROZEN"
}
```

---

### Update Card Controls & Spending Limits
`PATCH /api/v1/cards/{cardId}/controls`

Configures monthly limits, e-commerce switches, and international transaction flags.

#### Request:
```json
{
  "monthlyLimit": 2500.00,
  "onlinePaymentsEnabled": true,
  "internationalEnabled": false
}
```

---

## 11. Standard Error Format (RFC 7807 Problem Details)

All error responses return a standard structured payload:

```json
{
  "type": "https://api.fintech.example.com/errors/insufficient-funds",
  "title": "Insufficient Funds",
  "status": 400,
  "detail": "Account balance of $25.00 is insufficient for requested transfer of $50.00.",
  "instance": "/api/v1/transfers",
  "code": "INSUFFICIENT_FUNDS",
  "timestamp": "2026-10-31T15:35:10Z"
}
```

### Common Error Codes
| HTTP Status | Code | Meaning |
| :--- | :--- | :--- |
| `400 Bad Request` | `INSUFFICIENT_FUNDS` | Sender does not have enough available balance |
| `400 Bad Request` | `CURRENCY_MISMATCH` | Transfer attempted between differing currencies without FX quote |
| `401 Unauthorized`| `INVALID_CREDENTIALS`| Missing or invalid JWT token |
| `409 Conflict`    | `IDEMPOTENCY_IN_FLIGHT`| Another request with this idempotency key is currently processing |
| `422 Unprocessable`| `ACCOUNT_SUSPENDED` | Account is frozen by risk/compliance check |


## Saved Beneficiaries

All endpoints require a bearer token. Ownership comes from the authenticated user; a user ID is never accepted from the client.

| Method | Endpoint | Result |
| --- | --- | --- |
| GET | `/api/v1/beneficiaries` | List your saved recipients, newest first |
| POST | `/api/v1/beneficiaries` | Save a recipient (201) |
| PATCH | `/api/v1/beneficiaries/{id}` | Rename with `{"nickname":"Rent"}` |
| DELETE | `/api/v1/beneficiaries/{id}` | Remove a saved recipient (204) |

Wallet example:
```json
{"type":"WALLET","nickname":"Alex","recipientAccountId":"550e8400-e29b-41d4-a716-446655440000","currency":"USD"}
```

Bank example:
```json
{"type":"BANK","nickname":"Rent","bankCode":"021000021","accountNumber":"0012345678","accountName":"Alex Morgan","currency":"USD"}
```

Wallet recipients must be active customer liability accounts, owned by another customer, in the specified currency. Bank details are format validated; saving a beneficiary does not verify ownership of the external bank account. Currency and bank code are normalized to uppercase and surrounding whitespace is removed. Account numbers retain leading zeros. Each owner may save a wallet once, or a bank destination once per currency. Duplicate saves return 409, invalid fields return 400, and missing or other users' beneficiary IDs return 404.

Transfer and payout forms include saved recipient selection plus save, rename, and remove controls. Selecting a recipient fills the existing destination fields. Payments continue through the existing transfer/payout endpoints and retain their balance, KYC, ownership and idempotency checks. Removing a saved recipient does not cancel payments or clear an entered destination.
