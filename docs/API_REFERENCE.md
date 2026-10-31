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
`POST /api/v1/webhooks/stripe`

#### Headers:
- `Stripe-Signature: t=1614552222,v1=5257a869e7ecebeda32affa62cd49f...`

#### Request Body:
```json
{
  "id": "evt_1MvBEw2eZvKYlo2C0VXYZABC",
  "type": "payment_intent.succeeded",
  "data": {
    "object": {
      "id": "pi_3MvBEw2eZvKYlo2C0XYZABC",
      "amount": 10000,
      "currency": "usd",
      "metadata": {
        "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
        "accountId": "a3b9f42b-5861-4685-b9f4-2c6d48259b12"
      }
    }
  }
}
```

#### Response: `200 OK`
```json
{
  "received": true
}
```

---

## 5. Standard Error Format (RFC 7807 Problem Details)

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

