"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api, generateUUID } from "@/lib/api";
import { AccountBalance, PaymentRequest } from "@/types";
import {
  Receipt,
  PlusCircle,
  Copy,
  Check,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  Send,
  XCircle,
  Search,
} from "lucide-react";

export default function PaymentRequestsPage() {
  const { user, isLoading } = useAuth();
  const router = useRouter();

  const [sent, setSent] = useState<PaymentRequest[]>([]);
  const [incoming, setIncoming] = useState<PaymentRequest[]>([]);
  const [accounts, setAccounts] = useState<AccountBalance[]>([]);
  const [loading, setLoading] = useState(true);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Create request form
  const [requesterAccountId, setRequesterAccountId] = useState("");
  const [payerEmail, setPayerEmail] = useState("");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [creating, setCreating] = useState(false);

  // Lookup by code
  const [lookupCode, setLookupCode] = useState("");
  const [lookedUp, setLookedUp] = useState<PaymentRequest | null>(null);

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    }
  }, [user, isLoading, router]);

  const loadData = async () => {
    if (!user) return;
    setLoading(true);
    try {
      const [sentRes, incomingRes, accountsRes] = await Promise.all([
        api.get<PaymentRequest[]>("/payment-requests/sent"),
        api.get<PaymentRequest[]>("/payment-requests/incoming"),
        api.get<AccountBalance[]>(`/accounts/user/${user.id}`),
      ]);
      setSent(sentRes.data);
      setIncoming(incomingRes.data);
      setAccounts(accountsRes.data);
      if (!requesterAccountId && accountsRes.data.length > 0) {
        setRequesterAccountId(accountsRes.data[0].accountId);
      }
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || "Failed to load payment requests");
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
    setCreating(true);
    try {
      const res = await api.post<PaymentRequest>("/payment-requests", {
        requesterAccountId,
        payerEmail: payerEmail.trim() || undefined,
        amount: Number(amount),
        description: description.trim() || undefined,
      });
      setSent((prev) => [res.data, ...prev]);
      setPayerEmail("");
      setAmount("");
      setDescription("");
      setSuccessMessage(`Payment request ${res.data.requestCode} created (${res.data.amount} ${res.data.currency}).`);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || "Failed to create payment request");
    } finally {
      setCreating(false);
    }
  };

  const handleLookup = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setLookedUp(null);
    try {
      const res = await api.get<PaymentRequest>(`/payment-requests/code/${encodeURIComponent(lookupCode.trim())}`);
      setLookedUp(res.data);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || "Payment request not found");
    }
  };

  const handlePay = async (reqItem: PaymentRequest) => {
    setErrorMessage(null);
    setSuccessMessage(null);
    const matchingWallet = accounts.find((a) => a.currency === reqItem.currency);
    if (!matchingWallet) {
      setErrorMessage(`You need an active ${reqItem.currency} wallet to pay this request.`);
      return;
    }
    try {
      const res = await api.post<PaymentRequest>(
        `/payment-requests/${reqItem.id}/pay`,
        { payerAccountId: matchingWallet.accountId },
        { headers: { "Idempotency-Key": generateUUID() } }
      );
      setIncoming((prev) => prev.map((r) => (r.id === reqItem.id ? res.data : r)));
      if (lookedUp?.id === reqItem.id) setLookedUp(res.data);
      setSuccessMessage(`Paid ${res.data.amount} ${res.data.currency} (Ref: ${res.data.transactionReference}).`);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || "Payment failed");
    }
  };

  const handleDeclineOrCancel = async (id: string, action: "decline" | "cancel") => {
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      const res = await api.post<PaymentRequest>(`/payment-requests/${id}/${action}`);
      if (action === "decline") {
        setIncoming((prev) => prev.map((r) => (r.id === id ? res.data : r)));
      } else {
        setSent((prev) => prev.map((r) => (r.id === id ? res.data : r)));
      }
      setSuccessMessage(`Payment request ${res.data.requestCode} is now ${res.data.status}.`);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || `Failed to ${action} request`);
    }
  };

  const copyCode = async (code: string) => {
    await navigator.clipboard.writeText(code);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(null), 1500);
  };

  if (isLoading || !user) return null;

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
              <Receipt className="w-6 h-6 text-blue-400" />
              Payment Requests &amp; Links
            </h1>
            <p className="text-sm text-slate-400 mt-1">
              Request money from other users or generate shareable REQ-codes settled atomically via the ledger.
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

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Create Payment Request */}
          <form onSubmit={handleCreate} className="lg:col-span-2 rounded-2xl border border-white/10 bg-slate-900/60 p-6 space-y-4">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">Request Money</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-400 mb-1.5">Receive Into Wallet</label>
                <select
                  value={requesterAccountId}
                  onChange={(e) => setRequesterAccountId(e.target.value)}
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
                <label className="block text-xs font-semibold text-slate-400 mb-1.5">Amount</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  required
                  placeholder="50.00"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-400 mb-1.5">Payer Email (Optional)</label>
                <input
                  type="email"
                  placeholder="Leave blank for open payment link"
                  value={payerEmail}
                  onChange={(e) => setPayerEmail(e.target.value)}
                  className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-400 mb-1.5">Description</label>
                <input
                  type="text"
                  placeholder="Invoice #104, dinner split..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>
            <div className="flex justify-end">
              <button
                type="submit"
                disabled={creating || accounts.length === 0}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-sm font-semibold text-white"
              >
                <PlusCircle className="w-4 h-4" />
                {creating ? "Creating..." : "Create Request"}
              </button>
            </div>
          </form>

          {/* Lookup & Pay by Code */}
          <form onSubmit={handleLookup} className="rounded-2xl border border-white/10 bg-slate-900/60 p-6 space-y-4">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">Pay by Request Code</h2>
            <div className="flex gap-2">
              <input
                type="text"
                required
                placeholder="REQ-XXXXXXXX"
                value={lookupCode}
                onChange={(e) => setLookupCode(e.target.value)}
                className="flex-1 rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm font-mono text-white focus:outline-none focus:border-blue-500"
              />
              <button
                type="submit"
                className="px-3.5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-white inline-flex items-center gap-1.5"
              >
                <Search className="w-4 h-4" />
                Find
              </button>
            </div>
            {lookedUp && (
              <div className="rounded-xl bg-slate-950/70 border border-white/10 p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-mono text-blue-300">{lookedUp.requestCode}</span>
                  <span className="text-xs font-semibold text-slate-300">{lookedUp.status}</span>
                </div>
                <div className="text-base font-bold text-white">
                  {lookedUp.amount.toLocaleString()} {lookedUp.currency}
                </div>
                <p className="text-xs text-slate-400">
                  To {lookedUp.requesterName} • {lookedUp.description}
                </p>
                {lookedUp.status === "PENDING" && lookedUp.requesterUserId !== user.id && (
                  <button
                    type="button"
                    onClick={() => handlePay(lookedUp)}
                    className="w-full mt-2 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-xs font-semibold text-white"
                  >
                    Pay {lookedUp.amount.toLocaleString()} {lookedUp.currency}
                  </button>
                )}
              </div>
            )}
          </form>
        </div>

        {/* Incoming Requests */}
        <section className="space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-400">Incoming Requests</h2>
          {incoming.length === 0 ? (
            <div className="rounded-2xl border border-white/10 bg-slate-900/40 p-6 text-sm text-slate-400">
              No incoming payment requests.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {incoming.map((r) => (
                <div key={r.id} className="rounded-2xl border border-white/10 bg-slate-900/60 p-5 flex items-center justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono text-blue-300">{r.requestCode}</span>
                      <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">{r.status}</span>
                    </div>
                    <div className="text-lg font-bold text-white mt-1">
                      {r.amount.toLocaleString()} {r.currency}
                    </div>
                    <p className="text-xs text-slate-400">
                      From {r.requesterName} • {r.description}
                    </p>
                  </div>
                  {r.status === "PENDING" && (
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handlePay(r)}
                        className="inline-flex items-center gap-1 px-3 py-2 rounded-xl bg-emerald-600/20 border border-emerald-500/30 text-emerald-300 hover:bg-emerald-600/30 text-xs font-semibold"
                      >
                        <Send className="w-3.5 h-3.5" />
                        Pay
                      </button>
                      <button
                        onClick={() => handleDeclineOrCancel(r.id, "decline")}
                        className="inline-flex items-center gap-1 px-3 py-2 rounded-xl bg-rose-500/10 border border-rose-500/25 text-rose-300 hover:bg-rose-500/20 text-xs font-semibold"
                      >
                        <XCircle className="w-3.5 h-3.5" />
                        Decline
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Sent Requests */}
        <section className="space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-400">Sent Requests</h2>
          {sent.length === 0 ? (
            <div className="rounded-2xl border border-white/10 bg-slate-900/40 p-6 text-sm text-slate-400">
              No payment requests sent yet.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {sent.map((r) => (
                <div key={r.id} className="rounded-2xl border border-white/10 bg-slate-900/60 p-5 flex items-center justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono text-blue-300">{r.requestCode}</span>
                      <button
                        type="button"
                        onClick={() => copyCode(r.requestCode)}
                        className="text-slate-400 hover:text-white"
                        title="Copy code"
                      >
                        {copiedCode === r.requestCode ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                      </button>
                      <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">{r.status}</span>
                    </div>
                    <div className="text-lg font-bold text-white mt-1">
                      {r.amount.toLocaleString()} {r.currency}
                    </div>
                    <p className="text-xs text-slate-400">
                      {r.payerEmail ? `To ${r.payerEmail}` : "Open payment link"} • {r.description}
                    </p>
                  </div>
                  {r.status === "PENDING" && (
                    <button
                      onClick={() => handleDeclineOrCancel(r.id, "cancel")}
                      className="inline-flex items-center gap-1 px-3 py-2 rounded-xl bg-rose-500/10 border border-rose-500/25 text-rose-300 hover:bg-rose-500/20 text-xs font-semibold"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      Cancel
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

