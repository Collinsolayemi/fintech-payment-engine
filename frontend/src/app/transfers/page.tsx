"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api, generateUUID } from "@/lib/api";
import { AccountBalance, TransferResponse } from "@/types";
import { 
  ArrowLeftRight, 
  CheckCircle2, 
  AlertCircle, 
  ShieldAlert, 
  Lock, 
  Copy, 
  Check, 
  Zap, 
  RefreshCw,
  Wallet,
  ArrowRight,
  ShieldCheck
} from "lucide-react";

export default function TransfersPage() {
  const { user, isLoading, refreshProfile } = useAuth();
  const router = useRouter();

  const [accounts, setAccounts] = useState<AccountBalance[]>([]);
  const [senderAccountId, setSenderAccountId] = useState("");
  const [recipientAccountId, setRecipientAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [receipt, setReceipt] = useState<TransferResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    } else if (user) {
      api.get<AccountBalance[]>(`/accounts/user/${user.id}`).then((res) => {
        setAccounts(res.data);
        if (res.data.length > 0) {
          setSenderAccountId(res.data[0].id || res.data[0].accountId);
        }
      });
    }
  }, [user, isLoading]);

  const activeAccount = accounts.find((a) => (a.id || a.accountId) === senderAccountId) || accounts[0];
  const activeBalance = activeAccount ? parseFloat(activeAccount.balance.toString()) : 0;

  const handleTransfer = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setReceipt(null);
    setSubmitting(true);

    const idempotencyKey = generateUUID();

    try {
      const res = await api.post<TransferResponse>(
        "/transfers",
        {
          senderAccountId,
          recipientAccountId,
          amount: parseFloat(amount),
          currency: activeAccount?.currency || "USD",
          description: description || "Peer-to-peer wallet transfer",
        },
        {
          headers: {
            "Idempotency-Key": idempotencyKey,
          },
        }
      );

      setReceipt(res.data);
      setAmount("");
      setDescription("");
      await refreshProfile();
      
      // Reload accounts balance
      if (user?.id) {
        const accRes = await api.get<AccountBalance[]>(`/accounts/user/${user.id}`);
        setAccounts(accRes.data);
      }
    } catch (err: any) {
      const detail = err.response?.data?.detail || err.response?.data?.message || "Transfer failed. Please check balance and limits.";
      setError(detail);
    } finally {
      setSubmitting(false);
    }
  };

  const setPresetAmount = (val: number) => {
    setAmount(val.toString());
  };

  const copyReceiptRef = (ref: string) => {
    navigator.clipboard.writeText(ref);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (isLoading || !user) return null;

  return (
    <div className="min-h-screen bg-[#05070e] text-slate-100">
      <Navbar />

      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <div className="h-10 w-10 rounded-2xl bg-blue-600/20 text-blue-400 border border-blue-500/30 flex items-center justify-center">
                <ArrowLeftRight className="w-5 h-5" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                Peer-to-Peer Transfer
              </h1>
            </div>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Deterministic double-entry journal posting • Guaranteed idempotency with zero double-spending
            </p>
          </div>

          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/10 text-emerald-400 text-xs font-mono">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Pessimistic DB Lock Active</span>
          </div>
        </div>

        {/* KYC Limit Banner */}
        <div className="rounded-2xl border border-white/10 bg-slate-900/60 p-4 backdrop-blur-xl flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-blue-600/10 text-blue-400 border border-blue-500/20">
              <ShieldAlert className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs font-semibold text-slate-200">Daily KYC Transfer Allowance</div>
              <div className="text-[11px] text-slate-400">
                Tier {user.kycTier}: Used ${user.dailyUsedToday.toLocaleString()} of ${user.dailyLimit.toLocaleString()} limit
              </div>
            </div>
          </div>
          <button 
            onClick={() => router.push("/kyc")}
            className="text-xs font-semibold text-blue-400 hover:text-blue-300 transition-colors"
          >
            Upgrade Limits &rarr;
          </button>
        </div>

        {/* Transfer Form Box */}
        <div className="rounded-3xl border border-white/10 bg-slate-900/70 p-6 sm:p-8 backdrop-blur-2xl shadow-2xl shadow-blue-950/20 space-y-6">
          
          {error && (
            <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-start gap-3">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold">Transaction Declined: </span>
                <span>{error}</span>
              </div>
            </div>
          )}

          {receipt && (
            <div className="p-6 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm font-bold text-emerald-400">
                  <CheckCircle2 className="w-5 h-5" />
                  <span>Transfer Successfully Committed to Ledger</span>
                </div>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 font-bold">
                  POSTED
                </span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2 text-xs font-mono">
                <div className="p-3 rounded-xl bg-slate-950/60 border border-white/5">
                  <span className="text-slate-400 block text-[10px] uppercase">Transaction Ref</span>
                  <div className="flex items-center gap-2 text-white font-bold mt-1">
                    <span className="truncate max-w-[120px]">{receipt.reference}</span>
                    <button onClick={() => copyReceiptRef(receipt.reference)} className="text-slate-400 hover:text-white">
                      {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    </button>
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-slate-950/60 border border-white/5">
                  <span className="text-slate-400 block text-[10px] uppercase">Amount Settled</span>
                  <span className="text-emerald-400 font-bold text-sm block mt-1">
                    ${receipt.amount?.toLocaleString("en-US", { minimumFractionDigits: 2 })} {receipt.currency}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-slate-950/60 border border-white/5">
                  <span className="text-slate-400 block text-[10px] uppercase">Status</span>
                  <span className="text-white font-bold block mt-1">
                    {receipt.status || "COMPLETED"}
                  </span>
                </div>
              </div>

              <div className="text-[11px] text-emerald-400/80 font-mono">
                Double-Entry Posting: Debit applied to sender account, credit posted to recipient account in atomic transaction.
              </div>
            </div>
          )}

          <form onSubmit={handleTransfer} className="space-y-6">
            
            {/* Sender Account */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                Source Account (Debit)
              </label>
              <div className="p-4 rounded-2xl bg-slate-950 border border-slate-700/80 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-xl bg-blue-600/10 text-blue-400 border border-blue-500/20">
                    <Wallet className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-white">
                      Primary {activeAccount?.currency || "USD"} Wallet
                    </div>
                    <div className="text-xs font-mono text-slate-400 truncate max-w-[200px] sm:max-w-none">
                      {activeAccount?.id || activeAccount?.accountId || "Loading account..."}
                    </div>
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-[10px] font-mono text-slate-400 uppercase">Available</div>
                  <div className="text-sm font-bold font-mono text-emerald-400">
                    ${activeBalance.toLocaleString("en-US", { minimumFractionDigits: 2 })}
                  </div>
                </div>
              </div>
            </div>

            {/* Recipient Account Input */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider">
                  Recipient Account UUID (Credit)
                </label>
                <span className="text-[11px] text-slate-400">Must be a valid UUID</span>
              </div>
              <input
                type="text"
                required
                value={recipientAccountId}
                onChange={(e) => setRecipientAccountId(e.target.value)}
                placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
                className="w-full px-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white font-mono text-sm focus:outline-none focus:border-blue-500 placeholder:text-slate-600"
              />
            </div>

            {/* Amount Input with Quick Pills */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider">
                  Transfer Amount ({activeAccount?.currency || "USD"})
                </label>
                <div className="flex items-center gap-1.5">
                  {[25, 50, 100, 250].map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      onClick={() => setPresetAmount(preset)}
                      className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-[11px] font-mono text-slate-300 border border-white/5 transition-colors"
                    >
                      +${preset}
                    </button>
                  ))}
                  {activeBalance > 0 && (
                    <button
                      type="button"
                      onClick={() => setPresetAmount(activeBalance)}
                      className="px-2.5 py-1 rounded-lg bg-blue-600/20 text-blue-400 border border-blue-500/30 text-[11px] font-mono font-bold hover:bg-blue-600/30 transition-colors"
                    >
                      MAX
                    </button>
                  )}
                </div>
              </div>
              <div className="relative">
                <span className="absolute left-4 top-3.5 text-lg font-bold font-mono text-slate-400">$</span>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  max={activeBalance}
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                  className="w-full pl-9 pr-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white font-mono text-lg font-bold focus:outline-none focus:border-blue-500 placeholder:text-slate-600"
                />
              </div>
            </div>

            {/* Note / Description */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                Transfer Reference Note (Optional)
              </label>
              <input
                type="text"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Invoice payment, dinner share, etc."
                className="w-full px-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white text-sm focus:outline-none focus:border-blue-500 placeholder:text-slate-600"
              />
            </div>

            {/* Breakdown summary */}
            <div className="p-4 rounded-2xl bg-slate-950/80 border border-white/5 space-y-2 text-xs">
              <div className="flex justify-between text-slate-400">
                <span>Platform Network Fee:</span>
                <span className="text-emerald-400 font-mono font-bold">$0.00 (Zero Fee Internal)</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span>Settlement Speed:</span>
                <span className="text-white font-mono">Instant (Atomic DB Commit)</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span>Concurrency Protection:</span>
                <span className="text-blue-400 font-mono">Pessimistic Order Lock</span>
              </div>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={submitting || !amount || parseFloat(amount) <= 0 || !recipientAccountId}
              className="w-full py-4 rounded-2xl font-bold text-sm text-white bg-gradient-to-r from-blue-600 via-indigo-600 to-blue-600 hover:from-blue-500 hover:to-indigo-500 shadow-xl shadow-blue-500/25 transition-all flex items-center justify-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {submitting ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" />
                  <span>Posting Double-Entry Transaction...</span>
                </>
              ) : (
                <>
                  <span>Send ${amount ? parseFloat(amount).toFixed(2) : "0.00"} Now</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>
        </div>
      </main>
    </div>
  );
}
