"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api, generateUUID } from "@/lib/api";
import { AccountBalance, TransferResponse } from "@/types";
import { ArrowLeftRight, CheckCircle2, AlertCircle, ShieldAlert } from "lucide-react";

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

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    } else if (user) {
      api.get<AccountBalance[]>(`/accounts/user/${user.id}`).then((res) => {
        setAccounts(res.data);
        if (res.data.length > 0) {
          setSenderAccountId(res.data[0].accountId);
        }
      });
    }
  }, [user, isLoading]);

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
          currency: "USD",
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
      // reload accounts balance
      if (user?.id) {
        const accRes = await api.get<AccountBalance[]>(`/accounts/user/${user.id}`);
        setAccounts(accRes.data);
      }
    } catch (err: any) {
      const detail = err.response?.data?.detail || err.response?.data?.message || "Transfer failed";
      setError(detail);
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading || !user) return null;

  return (
    <div className="min-h-screen bg-slate-950">
      <Navbar />

      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
            <ArrowLeftRight className="w-6 h-6 text-blue-500" />
            Peer-to-Peer Transfer
          </h1>
          <p className="text-sm text-slate-400">
            Execute race-condition safe, double-entry transfers with deterministic account locking.
          </p>
        </div>

        {/* KYC Limit Banner */}
        <div className="rounded-xl bg-blue-950/40 border border-blue-500/20 p-4 flex items-start gap-3">
          <ShieldAlert className="w-5 h-5 text-blue-400 shrink-0 mt-0.5" />
          <div className="text-xs space-y-1">
            <span className="font-semibold text-blue-300">
              Active Tier {user.kycTier} Limits:
            </span>
            <div className="text-slate-400">
              Single transfer limit: <strong className="text-slate-200">${user.singleTransactionLimit.toLocaleString()}</strong> • 
              Daily limit remaining: <strong className="text-slate-200">${user.dailyRemainingToday.toLocaleString()}</strong>
            </div>
          </div>
        </div>

        {error && (
          <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-sm flex items-start gap-3">
            <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
            <div>
              <div className="font-semibold">Transfer Failed</div>
              <div>{error}</div>
            </div>
          </div>
        )}

        {receipt && (
          <div className="p-6 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 space-y-3">
            <div className="flex items-center gap-2 font-bold text-lg text-emerald-400">
              <CheckCircle2 className="w-5 h-5" /> Transfer Executed Successfully
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs pt-2">
              <div>
                <span className="text-slate-400 block">Reference:</span>
                <span className="font-mono font-medium text-white">{receipt.reference}</span>
              </div>
              <div>
                <span className="text-slate-400 block">Amount Sent:</span>
                <span className="font-mono font-bold text-white text-sm">
                  ${receipt.amount.toFixed(2)} {receipt.currency}
                </span>
              </div>
              <div>
                <span className="text-slate-400 block">Status:</span>
                <span className="font-semibold text-emerald-400">{receipt.status}</span>
              </div>
              <div>
                <span className="text-slate-400 block">Timestamp:</span>
                <span className="text-slate-300">{new Date(receipt.createdAt).toLocaleTimeString()}</span>
              </div>
            </div>
          </div>
        )}

        {/* Transfer Form Card */}
        <div className="rounded-2xl bg-slate-900 border border-slate-800 p-6 sm:p-8 shadow-xl">
          <form onSubmit={handleTransfer} className="space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                  Debit Account (Sender)
                </label>
                <select
                  value={senderAccountId}
                  onChange={(e) => setSenderAccountId(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-white text-sm focus:outline-none focus:border-blue-500"
                >
                  {accounts.map((acc) => (
                    <option key={acc.accountId} value={acc.accountId}>
                      {acc.accountName} (${acc.balance.toFixed(2)} {acc.currency})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                  Recipient Account ID (UUID)
                </label>
                <input
                  type="text"
                  required
                  value={recipientAccountId}
                  onChange={(e) => setRecipientAccountId(e.target.value)}
                  placeholder="e.g. 00000000-0000-0000-0000-000000001001"
                  className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-white text-sm font-mono focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                Transfer Amount ($ USD)
              </label>
              <div className="relative">
                <span className="absolute left-4 top-2.5 text-slate-500 font-semibold">$</span>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                  className="w-full pl-8 pr-4 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-white text-sm font-mono focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                Memo / Description (Optional)
              </label>
              <input
                type="text"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Payment for invoice #1024"
                className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-white text-sm focus:outline-none focus:border-blue-500"
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="w-full py-3 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm transition-colors shadow-lg shadow-blue-500/20 disabled:opacity-50"
            >
              {submitting ? "Processing Transfer..." : "Confirm & Send Funds"}
            </button>
          </form>
        </div>
      </main>
    </div>
  );
}

