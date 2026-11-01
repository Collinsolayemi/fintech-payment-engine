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

## 7. Standard Error Format (RFC 7807 Problem Details)

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

