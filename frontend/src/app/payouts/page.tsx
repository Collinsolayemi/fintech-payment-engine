"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api, generateUUID } from "@/lib/api";
import { AccountBalance, PayoutResponse } from "@/types";
import { 
  Landmark, 
  ArrowUpRight, 
  CheckCircle2, 
  Clock, 
  AlertTriangle, 
  RefreshCw,
  Building,
  ShieldCheck,
  Check,
  Copy,
  Info
} from "lucide-react";

const SUPPORTED_BANKS = [
  { code: "021000021", name: "JPMorgan Chase", rail: "Instant FedNow" },
  { code: "011000138", name: "Bank of America", rail: "RTP / ACH" },
  { code: "121000248", name: "Wells Fargo", rail: "Same-Day ACH" },
  { code: "021000089", name: "Citibank N.A.", rail: "Fedwire" },
];

export default function PayoutsPage() {
  const { user, isLoading, refreshProfile } = useAuth();
  const router = useRouter();

  const [accounts, setAccounts] = useState<AccountBalance[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [bankCode, setBankCode] = useState(SUPPORTED_BANKS[0].code);
  const [accountNumber, setAccountNumber] = useState("");
  const [accountName, setAccountName] = useState("");
  const [payouts, setPayouts] = useState<PayoutResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [copied, setCopied] = useState<string | null>(null);

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    } else if (user) {
      loadData();
    }
  }, [user, isLoading]);

  const loadData = async () => {
    try {
      if (user?.id) {
        const accRes = await api.get<AccountBalance[]>(`/accounts/user/${user.id}`);
        setAccounts(accRes.data);
        if (accRes.data.length > 0) {
          setSelectedAccountId(accRes.data[0].id || accRes.data[0].accountId);
        }
      }
      const pRes = await api.get<PayoutResponse[]>("/payouts/my-payouts");
      setPayouts(pRes.data || []);
    } catch (e) {
      console.error(e);
    }
  };

  const activeAccount = accounts.find((a) => (a.id || a.accountId) === selectedAccountId) || accounts[0];
  const activeBalance = activeAccount ? parseFloat(activeAccount.balance.toString()) : 0;

  const handleInitiatePayout = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setSubmitting(true);

    try {
      const idempotencyKey = generateUUID();
      const res = await api.post<PayoutResponse>(
        "/payouts/initiate",
        {
          accountId: selectedAccountId,
          amount: parseFloat(amount),
          currency: activeAccount?.currency || "USD",
          destinationBankCode: bankCode,
          destinationAccountNumber: accountNumber,
          destinationAccountName: accountName,
        },
        {
          headers: {
            "Idempotency-Key": idempotencyKey,
          },
        }
      );

      setSuccess(`Payout ${res.data.reference} successfully queued in settlement escrow.`);
      setAmount("");
      setAccountNumber("");
      setAccountName("");
      await refreshProfile();
      loadData();
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.response?.data?.message || "Payout initiation failed. Check your balance.";
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(text);
    setTimeout(() => setCopied(null), 2000);
  };

  if (isLoading || !user) return null;

  return (
    <div className="min-h-screen bg-[#05070e] text-slate-100">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <div className="h-10 w-10 rounded-2xl bg-emerald-600/20 text-emerald-400 border border-emerald-500/30 flex items-center justify-center">
                <Landmark className="w-5 h-5" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                External Bank Payouts
              </h1>
            </div>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Automated escrow reservation • Two-phase cryptographic settlement rail
            </p>
          </div>

          <div className="flex items-center gap-2 px-3.5 py-1.5 rounded-full border border-blue-500/20 bg-blue-500/10 text-blue-400 text-xs font-mono">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Escrow Invariant Active</span>
          </div>
        </div>

        {/* Two-Column Grid: Form & Info */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          
          {/* Left Column: Form */}
          <div className="lg:col-span-7 rounded-3xl border border-white/10 bg-slate-900/70 p-6 sm:p-8 backdrop-blur-2xl shadow-xl space-y-6">
            
            <div className="flex items-center justify-between border-b border-white/5 pb-4">
              <div>
                <h2 className="text-base font-bold text-white">Withdrawal Details</h2>
                <p className="text-xs text-slate-400">Funds are held in escrow pending external bank clearance</p>
              </div>
              <span className="text-xs font-mono font-bold text-emerald-400">
                Fee: $1.50
              </span>
            </div>

            {error && (
              <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs">
                {error}
              </div>
            )}

            {success && (
              <div className="p-4 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 shrink-0" />
                <span>{success}</span>
              </div>
            )}

            <form onSubmit={handleInitiatePayout} className="space-y-5">
              
              {/* Select Source Account */}
              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                  Debit Account (Wallet)
                </label>
                <select
                  value={selectedAccountId}
                  onChange={(e) => setSelectedAccountId(e.target.value)}
                  className="w-full px-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white font-mono text-sm focus:outline-none focus:border-blue-500"
                >
                  {accounts.map((acc) => (
                    <option key={acc.id || acc.accountId} value={acc.id || acc.accountId}>
                      {acc.currency} Wallet — Balance: ${parseFloat(acc.balance.toString()).toFixed(2)} ({acc.id || acc.accountId})
                    </option>
                  ))}
                </select>
              </div>

              {/* Destination Bank Grid */}
              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                  Destination Banking Partner
                </label>
                <div className="grid grid-cols-2 gap-2.5">
                  {SUPPORTED_BANKS.map((bank) => (
                    <button
                      key={bank.code}
                      type="button"
                      onClick={() => setBankCode(bank.code)}
                      className={`p-3 rounded-2xl border text-left transition-all ${
                        bankCode === bank.code
                          ? "bg-blue-600/20 border-blue-500/50 text-white shadow-md shadow-blue-500/10"
                          : "bg-slate-950/80 border-white/5 text-slate-400 hover:text-white hover:bg-slate-800"
                      }`}
                    >
                      <div className="text-xs font-bold text-slate-200">{bank.name}</div>
                      <div className="text-[10px] font-mono text-blue-400 mt-0.5">{bank.rail}</div>
                    </button>
                  ))}
                </div>
              </div>

              {/* Account Number & Beneficiary Name */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                    Routing / Account Number
                  </label>
                  <input
                    type="text"
                    required
                    value={accountNumber}
                    onChange={(e) => setAccountNumber(e.target.value)}
                    placeholder="021000021-994821"
                    className="w-full px-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white font-mono text-sm focus:outline-none focus:border-blue-500 placeholder:text-slate-600"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                    Beneficiary Legal Name
                  </label>
                  <input
                    type="text"
                    required
                    value={accountName}
                    onChange={(e) => setAccountName(e.target.value)}
                    placeholder="Johnathan Doe"
                    className="w-full px-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white text-sm focus:outline-none focus:border-blue-500 placeholder:text-slate-600"
                  />
                </div>
              </div>

              {/* Amount */}
              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                  Payout Amount ({activeAccount?.currency || "USD"})
                </label>
                <div className="relative">
                  <span className="absolute left-4 top-3 text-lg font-mono font-bold text-slate-400">$</span>
                  <input
                    type="number"
                    step="0.01"
                    min="1.00"
                    max={activeBalance}
                    required
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="0.00"
                    className="w-full pl-9 pr-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white font-mono text-lg font-bold focus:outline-none focus:border-blue-500 placeholder:text-slate-600"
                  />
                </div>
              </div>

              {/* Action Button */}
              <button
                type="submit"
                disabled={submitting || !amount || parseFloat(amount) <= 0 || !accountNumber}
                className="w-full py-4 rounded-2xl font-bold text-sm text-white bg-gradient-to-r from-emerald-600 via-teal-600 to-emerald-600 hover:from-emerald-500 hover:to-teal-500 shadow-xl shadow-emerald-500/20 transition-all flex items-center justify-center gap-2 disabled:opacity-40"
              >
                {submitting ? (
                  <RefreshCw className="w-4 h-4 animate-spin" />
                ) : (
                  <>
                    <span>Initiate Escrow Payout</span>
                    <ArrowUpRight className="w-4 h-4" />
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Right Column: Escrow Mechanism & Security Info */}
          <div className="lg:col-span-5 space-y-6">
            
            {/* Escrow Workflow Visualizer */}
            <div className="rounded-3xl border border-white/10 bg-slate-900/60 p-6 backdrop-blur-xl shadow-xl space-y-4">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <Info className="w-4 h-4 text-blue-400" /> Two-Phase Settlement Escrow
              </h3>

              <div className="space-y-3 text-xs text-slate-400 font-mono">
                <div className="p-3 rounded-2xl bg-slate-950/80 border border-white/5 space-y-1">
                  <div className="text-blue-400 font-bold">1. Phase 1: Escrow Reservation</div>
                  <div>Debit: User Liability Account</div>
                  <div>Credit: Pending Settlements Escrow</div>
                </div>

                <div className="p-3 rounded-2xl bg-slate-950/80 border border-white/5 space-y-1">
                  <div className="text-amber-400 font-bold">2. Phase 2: Bank Webhook Clearance</div>
                  <div>Debit: Pending Settlements Escrow</div>
                  <div>Credit: Platform Clearing Asset Account</div>
                </div>

                <div className="p-3 rounded-2xl bg-slate-950/80 border border-white/5 space-y-1">
                  <div className="text-rose-400 font-bold">3. Reversal On Failure</div>
                  <div>Full refund credited back to User Wallet if clearing rejects.</div>
                </div>
              </div>
            </div>

            {/* Compliance Note */}
            <div className="rounded-3xl border border-emerald-500/20 bg-emerald-500/5 p-6 backdrop-blur-xl text-xs space-y-2">
              <div className="text-emerald-400 font-bold flex items-center gap-2">
                <ShieldCheck className="w-4 h-4" /> Daily Outbound Limits Enforced
              </div>
              <p className="text-slate-400 leading-relaxed">
                Payout amounts count against your Tier {user.kycTier} daily limit (${user.dailyLimit.toLocaleString()}).
                Tier 3 verified customers enjoy unlimited real-time payouts.
              </p>
            </div>
          </div>
        </div>

        {/* Payout History Table */}
        <div className="rounded-3xl border border-white/10 bg-slate-900/60 p-6 sm:p-8 backdrop-blur-xl shadow-xl space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-bold text-white">Recent External Withdrawals</h2>
              <p className="text-xs text-slate-400">Track clearance status and settlement webhook receipts</p>
            </div>
            <button
              onClick={loadData}
              className="p-2 rounded-xl border border-white/10 text-slate-400 hover:text-white"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-white/5 text-slate-400 font-mono uppercase text-[10px] tracking-wider">
                  <th className="py-3 px-3">Reference</th>
                  <th className="py-3 px-3">Beneficiary Account</th>
                  <th className="py-3 px-3">Status</th>
                  <th className="py-3 px-3">Date</th>
                  <th className="py-3 px-3 text-right">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {payouts.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-10 text-center text-slate-500">
                      No payout records yet.
                    </td>
                  </tr>
                ) : (
                  payouts.map((p) => (
                    <tr key={p.reference} className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-mono font-medium text-slate-200">
                        <div className="flex items-center gap-2">
                          <span>{p.reference}</span>
                          <button
                            onClick={() => copyToClipboard(p.reference)}
                            className="text-slate-500 hover:text-slate-300"
                          >
                            {copied === p.reference ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                          </button>
                        </div>
                      </td>
                      <td className="py-3 px-3 text-slate-300 font-mono">
                        {p.destinationAccountNumber || "••••••••"} ({p.destinationBankCode})
                      </td>
                      <td className="py-3 px-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold tracking-wide ${
                          p.status === "COMPLETED"
                            ? "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30"
                            : p.status === "PENDING_ESCROW" || p.status === "PROCESSING"
                            ? "bg-amber-500/15 text-amber-400 border border-amber-500/30"
                            : "bg-slate-700/50 text-slate-400"
                        }`}>
                          {p.status}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-slate-400 font-mono text-[11px]">
                        {p.createdAt ? new Date(p.createdAt).toLocaleDateString() : "Just now"}
                      </td>
                      <td className="py-3 px-3 text-right font-mono font-bold text-rose-400">
                        -${parseFloat(p.amount.toString()).toFixed(2)} {p.currency}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </main>
    </div>
  );
}
