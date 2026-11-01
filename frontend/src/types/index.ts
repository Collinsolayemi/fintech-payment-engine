export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  role: string;
}

export interface UserProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  kycTier: number;
  kycTierDescription: string;
  singleTransactionLimit: number;
  dailyLimit: number;
  dailyUsedToday: number;
  dailyRemainingToday: number;
  kycStatus: "PENDING" | "APPROVED" | "REJECTED";
  phoneNumber?: string;
  addressLine?: string;
  city?: string;
  country?: string;
  postalCode?: string;
  dateOfBirth?: string;
  createdAt?: string;
}

export interface AccountBalance {
  id?: string;
  accountId: string;
  accountNumber: string;
  accountName: string;
  accountType: "ASSET" | "LIABILITY" | "EQUITY" | "REVENUE" | "EXPENSE";
  currency: string;
  status: "ACTIVE" | "FROZEN" | "CLOSED";
  balance: number;
  totalDebits: number;
  totalCredits: number;
}

export interface TransferRequest {
  senderAccountId: string;
  recipientAccountId: string;
  amount: number;
  currency: string;
  fee?: number;
  description?: string;
}

export interface TransferResponse {
  reference: string;
  senderAccountId: string;
  recipientAccountId: string;
  amount: number;
  fee: number;
  currency: string;
  status: "PENDING" | "COMPLETED" | "FAILED" | "REVERSED";
  description: string;
  createdAt: string;
}

export interface TransactionRecord {
  id?: string;
  reference: string;
  description?: string;
  status?: string;
  amount: number | string;
  currency?: string;
  createdAt?: string;
}

export interface PayoutResponse {
  id: string;
  reference: string;
  accountId: string;
  accountNumber: string;
  amount: number;
  fee: number;
  currency: string;
  status: "PENDING_ESCROW" | "PROCESSING" | "COMPLETED" | "FAILED" | "REVERSED";
  destinationBankCode: string;
  destinationAccountNumber: string;
  destinationAccountName: string;
  failureReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface KycApplication {
  id: string;
  userId: string;
  userEmail: string;
  targetTier: number;
  idType: "PASSPORT" | "DRIVERS_LICENSE" | "NATIONAL_ID" | "BVN" | "RESIDENCE_PERMIT";
  idNumber: string;
  documentUrl?: string;
  status: "SUBMITTED" | "APPROVED" | "REJECTED";
  rejectionReason?: string;
  reviewedBy?: string;
  createdAt: string;
}

export interface ReconciliationRun {
  id: string;
  type: "INTERNAL_INTEGRITY" | "GATEWAY_SETTLEMENT";
  status: "RUNNING" | "PASSED" | "FAILED" | "ERROR";
  provider?: "STRIPE" | "PAYSTACK" | "FLUTTERWAVE";
  triggeredBy: string;
  totalDebits?: number;
  totalCredits?: number;
  externalTotal?: number;
  ledgerTotal?: number;
  recordsChecked?: number;
  discrepancyCount: number;
  errorMessage?: string;
  startedAt: string;
  completedAt?: string;
  discrepancies?: Array<{
    id: string;
    type: string;
    severity: "CRITICAL" | "HIGH" | "MEDIUM";
    reference?: string;
    expectedAmount?: number;
    actualAmount?: number;
    details: string;
    createdAt: string;
  }>;
}

