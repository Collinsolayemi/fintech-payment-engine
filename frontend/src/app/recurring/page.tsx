"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api } from "@/lib/api";
import { AccountBalance, RecurringTransfer } from "@/types";
import {
  CalendarClock,
  PlusCircle,
  Play,
  PauseCircle,
  PlayCircle,
  XCircle,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";

const FREQUENCIES: Array<{ value: RecurringTransfer["frequency"]; label: string }> = [
  { value: "DAILY", label: "Daily" },
  { value: "WEEKLY", label: "Weekly" },
  { value: "BIWEEKLY", label: "Every 2 Weeks" },
  { value: "MONTHLY", label: "Monthly" },
];

export default function RecurringTransfersPage() {
  const { user, isLoading } = useAuth();
  const router = useRouter();

  const [orders, setOrders] = useState<RecurringTransfer[]>([]);
  const [accounts, setAccounts] = useState<AccountBalance[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [senderAccountId, setSenderAccountId] = useState("");
  const [recipientAccountId, setRecipientAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [frequency, setFrequency] = useState<RecurringTransfer["frequency"]>("MONTHLY");
  const [description, setDescription] = useState("");

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    }
  }, [user, isLoading, router]);

  const loadData = async () => {
    if (!user) return;
    setLoading(true);
    try {
      const [ordersRes, accountsRes] = await Promise.all([
        api.get<RecurringTransfer[]>("/recurring-transfers"),
        api.get<AccountBalance[]>(`/accounts/user/${user.id}`),
      ]);
      setOrders(ordersRes.data);
      setAccounts(accountsRes.data);
      if (!senderAccountId && accountsRes.data.length > 0) {
        setSenderAccountId(accountsRes.data[0].accountId);
      }
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || "Failed to load standing orders");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      loadData();
    }
  }, [user]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);
    setSubmitting(true);
    try {
      const res = await api.post<RecurringTransfer>("/recurring-transfers", {
        senderAccountId,
        recipientAccountId: recipientAccountId.trim(),
        amount: Number(amount),
        frequency,
        description: description.trim() || undefined,
      });
      setOrders((prev) => [res.data, ...prev]);
      setRecipientAccountId("");
      setAmount("");
      setDescription("");
      setSuccessMessage(`Standing order created (${res.data.frequency} ${res.data.amount} ${res.data.currency}).`);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || "Failed to create standing order");
    } finally {
      setSubmitting(false);
    }
  };

  const handleAction = async (id: string, action: "pause" | "resume" | "cancel" | "execute") => {
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      const res = await api.post<RecurringTransfer>(`/recurring-transfers/${id}/${action}`);
      setOrders((prev) => prev.map((item) => (item.id === id ? res.data : item)));
      setSuccessMessage(
        action === "execute"
          ? `Executed transfer ${res.data.lastTransactionReference || "successfully"}.`
          : `Standing order status updated to ${res.data.status}.`
      );
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || `Action "${action}" failed`);
    }
  };

  if (isLoading || !user) return null;

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
              <CalendarClock className="w-6 h-6 text-blue-400" />
              Scheduled &amp; Recurring Standing Orders
            </h1>
            <p className="text-sm text-slate-400 mt-1">
              Automate daily, weekly, biweekly, or monthly P2P transfers backed by the ACID double-entry ledger.
            </p>
          </div>
          <button
            onClick={loadData}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 border border-white/10 text-xs font-semibold text-slate-300 hover:bg-slate-800 self-start"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
            Refresh
          </button>
        </div>

        {errorMessage && (
          <div className="flex items-center gap-2.5 p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm">
            <AlertCircle className="w-5 h-5 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        {successMessage && (
          <div className="flex items-center gap-2.5 p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-sm">
            <CheckCircle2 className="w-5 h-5 shrink-0" />
            <span>{successMessage}</span>
          </div>
        )}

        <form onSubmit={handleCreate} className="rounded-2xl border border-white/10 bg-slate-900/60 p-6 space-y-4">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">New Standing Order</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-400 mb-1.5">Source Wallet</label>
              <select
                value={senderAccountId}
                onChange={(e) => setSenderAccountId(e.target.value)}
                required
                className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
              >
                {accounts.map((acc) => (
                  <option key={acc.accountId} value={acc.accountId}>
                    {acc.currency} — {acc.accountNumber} ({acc.balance.toLocaleString()})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-400 mb-1.5">Recipient Wallet ID (UUID)</label>
              <input
                type="text"
                required
                placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
                value={recipientAccountId}
                onChange={(e) => setRecipientAccountId(e.target.value)}
                className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-400 mb-1.5">Amount</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                required
                placeholder="100.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-400 mb-1.5">Frequency</label>
              <select
                value={frequency}
                onChange={(e) => setFrequency(e.target.value as RecurringTransfer["frequency"])}
                className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
              >
                {FREQUENCIES.map((f) => (
                  <option key={f.value} value={f.value}>
                    {f.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="md:col-span-2">
              <label className="block text-xs font-semibold text-slate-400 mb-1.5">Description</label>
              <input
                type="text"
                placeholder="Monthly rent, allowance, savings..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
              />
            </div>
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={submitting || accounts.length === 0}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-sm font-semibold text-white transition-colors"
            >
              <PlusCircle className="w-4 h-4" />
              {submitting ? "Scheduling..." : "Create Standing Order"}
            </button>
          </div>
        </form>

        <div className="space-y-4">
          {orders.length === 0 && !loading ? (
            <div className="rounded-2xl border border-white/10 bg-slate-900/40 p-10 text-center text-sm text-slate-400">
              No standing orders scheduled yet.
            </div>
          ) : (
            orders.map((order) => (
              <div
                key={order.id}
                className="rounded-2xl border border-white/10 bg-slate-900/60 p-5 flex flex-col lg:flex-row lg:items-center justify-between gap-4"
              >
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="px-2.5 py-0.5 rounded-lg bg-blue-500/15 border border-blue-500/30 text-blue-300 text-xs font-bold">
                      {order.frequency}
                    </span>
                    <span
                      className={`px-2.5 py-0.5 rounded-lg border text-xs font-semibold ${
                        order.status === "ACTIVE"
                          ? "bg-emerald-500/15 text-emerald-400 border-emerald-500/30"
                          : order.status === "PAUSED"
                          ? "bg-amber-500/15 text-amber-400 border-amber-500/30"
                          : "bg-slate-700/40 text-slate-300 border-white/10"
                      }`}
                    >
                      {order.status}
                    </span>
                    <span className="text-base font-bold text-white">
                      {order.amount.toLocaleString()} {order.currency}
                    </span>
                  </div>
                  <p className="text-sm text-slate-300">
                    {order.senderAccountNumber} → {order.recipientAccountName} ({order.recipientAccountNumber})
                  </p>
                  <p className="text-xs text-slate-400">
                    {order.description} • Executions: {order.executionCount} • Next run:{" "}
                    {new Date(order.nextRunAt).toLocaleString()}
                    {order.lastTransactionReference ? ` • Last Ref: ${order.lastTransactionReference}` : ""}
                  </p>
                  {order.lastFailureReason && (
                    <p className="text-xs text-rose-400">Last error: {order.lastFailureReason}</p>
                  )}
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  {order.status === "ACTIVE" && (
                    <>
                      <button
                        onClick={() => handleAction(order.id, "execute")}
                        className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-emerald-600/20 border border-emerald-500/30 text-emerald-300 hover:bg-emerald-600/30 text-xs font-semibold"
                      >
                        <Play className="w-3.5 h-3.5" />
                        Run Now
                      </button>
                      <button
                        onClick={() => handleAction(order.id, "pause")}
                        className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-amber-500/10 border border-amber-500/25 text-amber-300 hover:bg-amber-500/20 text-xs font-semibold"
                      >
                        <PauseCircle className="w-3.5 h-3.5" />
                        Pause
                      </button>
                    </>
                  )}
                  {order.status === "PAUSED" && (
                    <button
                      onClick={() => handleAction(order.id, "resume")}
                      className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-blue-500/15 border border-blue-500/30 text-blue-300 hover:bg-blue-500/25 text-xs font-semibold"
                    >
                      <PlayCircle className="w-3.5 h-3.5" />
                      Resume
                    </button>
                  )}
                  {(order.status === "ACTIVE" || order.status === "PAUSED") && (
                    <button
                      onClick={() => handleAction(order.id, "cancel")}
                      className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-rose-500/10 border border-rose-500/25 text-rose-300 hover:bg-rose-500/20 text-xs font-semibold"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      Cancel
                    </button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </main>
    </div>
  );
}

