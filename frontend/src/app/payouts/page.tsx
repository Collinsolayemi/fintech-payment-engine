"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api, generateUUID } from "@/lib/api";
import { AccountBalance, PayoutResponse } from "@/types";
import { Landmark, ArrowUpRight, CheckCircle2, Clock, AlertTriangle, RefreshCw } from "lucide-react";

export default function PayoutsPage() {
  const { user, isLoading, refreshProfile } = useAuth();
  const router = useRouter();

  const [accounts, setAccounts] = useState<AccountBalance[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [bankCode, setBankCode] = useState("044");
  const [accountNumber, setAccountNumber] = useState("");
  const [accountName, setAccountName] = useState("");
  const [payouts, setPayouts] = useState<PayoutResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

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
          setSelectedAccountId(accRes.data[0].accountId);
        }
      }
      const pRes = await api.get<PayoutResponse[]>("/payouts/my-payouts");
      setPayouts(pRes.data);
    } catch (e) {
      console.error(e);
    }
  };

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
          currency: "USD",
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

      setSuccess(`Payout ${res.data.reference} initiated and placed in settlement escrow.`);
      setAmount("");
      setAccountNumber("");
      setAccountName("");
      await refreshProfile();
      loadData();
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.response?.data?.message || "Payout initiation failed";
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading || !user) return null;

  return (
    <div className="min-h-screen bg-slate-950">
      <Navbar />

      <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
            <Landmark className="w-6 h-6 text-blue-500" />
            External Bank Payouts
          </h1>
          <p className="text-sm text-slate-400">
            Two-phase escrow settlement: funds are locked in platform escrow before external clearing.
          </p>
        </div>

        {error && (
          <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-sm">
            {error}
          </div>
        )}

        {success && (
          <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-sm flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            {success}
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Initiation Form */}
          <div className="lg:col-span-1 rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-xl h-fit">
            <h2 className="text-base font-bold text-white mb-4">Request Withdrawal</h2>

            <form onSubmit={handleInitiatePayout} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Source Wallet
                </label>
                <select
                  value={selectedAccountId}
                  onChange={(e) => setSelectedAccountId(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                >
                  {accounts.map((acc) => (
                    <option key={acc.accountId} value={acc.accountId}>
                      {acc.accountName} (${acc.balance.toFixed(2)})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Amount ($ USD)
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="1"
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="100.00"
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm font-mono focus:outline-none focus:border-blue-500"
                />
                <span className="text-[11px] text-slate-500 block mt-1">+ $2.00 flat platform fee</span>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Bank Code / Routing #
                </label>
                <input
                  type="text"
                  required
                  value={bankCode}
                  onChange={(e) => setBankCode(e.target.value)}
                  placeholder="044 (Access) / 058 (GTB)"
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Account Number
                </label>
                <input
                  type="text"
                  required
                  value={accountNumber}
                  onChange={(e) => setAccountNumber(e.target.value)}
                  placeholder="0123456789"
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm font-mono focus:outline-none focus:border-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Account Holder Name
                </label>
                <input
                  type="text"
                  required
                  value={accountName}
                  onChange={(e) => setAccountName(e.target.value)}
                  placeholder="Johnathan Doe"
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                />
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="w-full py-2.5 px-4 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm transition-colors shadow-lg shadow-blue-500/20 disabled:opacity-50"
              >
                {submitting ? "Initiating..." : "Initiate Escrow Lock"}
              </button>
            </form>
          </div>

          {/* Previous Payouts Table */}
          <div className="lg:col-span-2 rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-xl">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-bold text-white">Recent Payout Settlements</h2>
              <button
                onClick={loadData}
                className="text-xs text-slate-400 hover:text-white flex items-center gap-1"
              >
                <RefreshCw className="w-3.5 h-3.5" /> Refresh
              </button>
            </div>

            {payouts.length === 0 ? (
              <div className="py-12 text-center text-slate-500 text-sm">
                No outbound payouts requested yet.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="uppercase bg-slate-950 text-slate-400 border-b border-slate-800">
                    <tr>
                      <th className="py-2.5 px-3">Reference</th>
                      <th className="py-2.5 px-3">Destination</th>
                      <th className="py-2.5 px-3">Amount</th>
                      <th className="py-2.5 px-3">Status</th>
                      <th className="py-2.5 px-3">Date</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono">
                    {payouts.map((p) => {
                      const isComplete = p.status === "COMPLETED";
                      const isPending = p.status === "PROCESSING" || p.status === "PENDING_ESCROW";
                      return (
                        <tr key={p.id} className="hover:bg-slate-800/30">
                          <td className="py-3 px-3 font-medium text-white">{p.reference}</td>
                          <td className="py-3 px-3 font-sans">
                            <div className="text-white">{p.destinationAccountName}</div>
                            <div className="text-slate-500 text-[11px]">{p.destinationAccountNumber} ({p.destinationBankCode})</div>
                          </td>
                          <td className="py-3 px-3 font-bold text-white">
                            ${p.amount.toFixed(2)}
                            <span className="text-[10px] text-slate-500 block font-normal">+${p.fee.toFixed(2)} fee</span>
                          </td>
                          <td className="py-3 px-3">
                            <span
                              className={`inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full font-medium ${
                                isComplete
                                  ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                                  : isPending
                                  ? "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                                  : "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                              }`}
                            >
                              {isComplete ? <CheckCircle2 className="w-3 h-3" /> : <Clock className="w-3 h-3" />}
                              {p.status}
                            </span>
                          </td>
                          <td className="py-3 px-3 text-slate-400 font-sans">
                            {new Date(p.createdAt).toLocaleDateString()}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

