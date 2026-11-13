"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api, generateUUID } from "@/lib/api";
import { VirtualAccount, VirtualAccountDepositResult } from "@/types";
import {
  Building2,
  PlusCircle,
  Copy,
  Check,
  ArrowDownLeft,
  PauseCircle,
  PlayCircle,
  XCircle,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  Landmark,
} from "lucide-react";

const SUPPORTED_CURRENCIES = [
  { code: "USD", label: "US Dollar (ACH / Wire)", flag: "🇺🇸" },
  { code: "GBP", label: "British Pound (FPS / IBAN)", flag: "🇬🇧" },
  { code: "EUR", label: "Euro (SEPA IBAN)", flag: "🇪🇺" },
  { code: "NGN", label: "Nigerian Naira (NUBAN)", flag: "🇳🇬" },
  { code: "CAD", label: "Canadian Dollar (EFT)", flag: "🇨🇦" },
];

export default function VirtualAccountsPage() {
  const { user, isLoading } = useAuth();
  const router = useRouter();

  const [virtualAccounts, setVirtualAccounts] = useState<VirtualAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCurrency, setSelectedCurrency] = useState("USD");
  const [creating, setCreating] = useState(false);
  const [copiedField, setCopiedField] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Inbound deposit simulation state
  const [depositTarget, setDepositTarget] = useState<VirtualAccount | null>(null);
  const [depositAmount, setDepositAmount] = useState("250.00");
  const [senderName, setSenderName] = useState("Acme Payroll Ltd");
  const [narration, setNarration] = useState("Client invoice settlement");
  const [depositing, setDepositing] = useState(false);
  const [lastDeposit, setLastDeposit] = useState<VirtualAccountDepositResult | null>(null);

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    }
  }, [user, isLoading, router]);

  const fetchVirtualAccounts = async () => {
    try {
      setLoading(true);
      const res = await api.get<VirtualAccount[]>("/virtual-accounts");
      setVirtualAccounts(res.data);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data?.detail;
      setErrorMessage(msg || "Failed to load virtual accounts");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      fetchVirtualAccounts();
    }
  }, [user]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);
    setCreating(true);
    try {
      const res = await api.post<VirtualAccount>("/virtual-accounts", {
        currency: selectedCurrency,
      });
      setVirtualAccounts((prev) => [res.data, ...prev]);
      setSuccessMessage(`${res.data.currency} virtual account (${res.data.accountNumber}) provisioned.`);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || "Unable to create virtual account");
    } finally {
      setCreating(false);
    }
  };

  const handleStatusChange = async (va: VirtualAccount, action: "suspend" | "activate" | "close") => {
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      const res = await api.post<VirtualAccount>(`/virtual-accounts/${va.id}/${action}`);
      setVirtualAccounts((prev) => prev.map((item) => (item.id === va.id ? res.data : item)));
      setSuccessMessage(`Virtual account ${va.accountNumber} is now ${res.data.status}.`);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || `Failed to ${action} virtual account`);
    }
  };

  const handleSimulateDeposit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!depositTarget) return;
    setErrorMessage(null);
    setSuccessMessage(null);
    setDepositing(true);
    try {
      const res = await api.post<VirtualAccountDepositResult>(
        `/virtual-accounts/${depositTarget.id}/simulate-deposit`,
        {
          amount: Number(depositAmount),
          senderName,
          narration,
        },
        {
          headers: {
            "Idempotency-Key": generateUUID(),
          },
        }
      );
      setLastDeposit(res.data);
      setSuccessMessage(
        `Credited ${res.data.amount.toLocaleString()} ${res.data.currency} to wallet. New balance: ${res.data.newWalletBalance.toLocaleString()} ${res.data.currency}.`
      );
      setDepositTarget(null);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
      setErrorMessage(data?.detail || data?.message || "Inbound deposit failed");
    } finally {
      setDepositing(false);
    }
  };

  const copyValue = async (key: string, value: string | null) => {
    if (!value) return;
    await navigator.clipboard.writeText(value);
    setCopiedField(key);
    setTimeout(() => setCopiedField(null), 1500);
  };

  if (isLoading || !user) return null;

  const existingCurrencies = new Set(virtualAccounts.map((v) => v.currency));

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
              <Building2 className="w-6 h-6 text-blue-400" />
              Virtual Receiving Accounts
            </h1>
            <p className="text-sm text-slate-400 mt-1">
              Dedicated local bank account numbers (ACH, Sort Code, SEPA IBAN, NUBAN) linked directly to your multi-currency ledger wallets.
            </p>
          </div>
          <button
            onClick={fetchVirtualAccounts}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 border border-white/10 text-xs font-semibold text-slate-300 hover:bg-slate-800 transition-colors self-start"
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

        {/* Provision New Virtual Account */}
        <form
          onSubmit={handleCreate}
          className="rounded-2xl border border-white/10 bg-slate-900/60 p-6 flex flex-col sm:flex-row items-stretch sm:items-end gap-4"
        >
          <div className="flex-1">
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
              Provision New Receiving Account
            </label>
            <select
              value={selectedCurrency}
              onChange={(e) => setSelectedCurrency(e.target.value)}
              className="w-full rounded-xl bg-slate-950 border border-white/10 px-4 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
            >
              {SUPPORTED_CURRENCIES.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.flag} {c.code} — {c.label} {existingCurrencies.has(c.code) ? "(Already Active)" : ""}
                </option>
              ))}
            </select>
          </div>
          <button
            type="submit"
            disabled={creating || existingCurrencies.has(selectedCurrency)}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-semibold text-white transition-colors"
          >
            <PlusCircle className="w-4 h-4" />
            {creating ? "Provisioning..." : "Create Virtual Account"}
          </button>
        </form>

        {/* Last Simulated Inbound Deposit Receipt */}
        {lastDeposit && (
          <div className="rounded-2xl border border-emerald-500/25 bg-emerald-950/20 p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <div className="text-xs font-mono uppercase tracking-wider text-emerald-400">
                Inbound Deposit Credited • {lastDeposit.transactionReference}
              </div>
              <div className="text-sm text-slate-200 mt-1">
                Received <span className="font-bold text-white">{lastDeposit.amount.toLocaleString()} {lastDeposit.currency}</span> from{" "}
                <span className="font-semibold">{lastDeposit.senderName}</span> ({lastDeposit.narration})
              </div>
            </div>
            <div className="text-right">
              <div className="text-xs text-slate-400">Updated Wallet Balance</div>
              <div className="text-lg font-bold font-mono text-emerald-300">
                {lastDeposit.newWalletBalance.toLocaleString()} {lastDeposit.currency}
              </div>
            </div>
          </div>
        )}

        {/* Virtual Accounts List */}
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {[1, 2].map((n) => (
              <div key={n} className="h-56 rounded-2xl border border-white/5 bg-slate-900/40 animate-pulse" />
            ))}
          </div>
        ) : virtualAccounts.length === 0 ? (
          <div className="rounded-2xl border border-white/10 bg-slate-900/40 p-12 text-center">
            <Landmark className="w-10 h-10 text-slate-500 mx-auto mb-3" />
            <h3 className="text-base font-semibold text-white">No Virtual Accounts Yet</h3>
            <p className="text-sm text-slate-400 mt-1">
              Select a currency above to generate dedicated receiving bank coordinates.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {virtualAccounts.map((va) => {
              const statusColor =
                va.status === "ACTIVE"
                  ? "bg-emerald-500/15 text-emerald-400 border-emerald-500/30"
                  : va.status === "SUSPENDED"
                  ? "bg-amber-500/15 text-amber-400 border-amber-500/30"
                  : "bg-rose-500/15 text-rose-400 border-rose-500/30";

              const details = [
                { label: "Account Name", value: va.accountName, key: `${va.id}-name` },
                { label: "Account Number", value: va.accountNumber, key: `${va.id}-num` },
                ...(va.routingNumber ? [{ label: "Routing Number", value: va.routingNumber, key: `${va.id}-routing` }] : []),
                ...(va.sortCode ? [{ label: "Sort Code", value: va.sortCode, key: `${va.id}-sort` }] : []),
                ...(va.iban ? [{ label: "IBAN", value: va.iban, key: `${va.id}-iban` }] : []),
                ...(va.bicSwift ? [{ label: "BIC / SWIFT", value: va.bicSwift, key: `${va.id}-bic` }] : []),
              ];

              return (
                <div
                  key={va.id}
                  className="rounded-2xl border border-white/10 bg-slate-900/60 p-6 flex flex-col justify-between gap-5"
                >
                  <div>
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="px-2.5 py-0.5 rounded-lg bg-blue-500/15 border border-blue-500/30 text-blue-300 text-xs font-bold">
                            {va.currency}
                          </span>
                          <span className={`px-2.5 py-0.5 rounded-lg border text-xs font-semibold ${statusColor}`}>
                            {va.status}
                          </span>
                        </div>
                        <h3 className="text-lg font-bold text-white mt-2">{va.bankName}</h3>
                        <p className="text-xs text-slate-400 font-mono">
                          Linked Wallet: {va.walletAccountNumber}
                        </p>
                      </div>
                    </div>

                    <div className="mt-5 space-y-2.5">
                      {details.map((row) => (
                        <div
                          key={row.key}
                          className="flex items-center justify-between gap-3 px-3.5 py-2 rounded-xl bg-slate-950/70 border border-white/5"
                        >
                          <span className="text-xs text-slate-400">{row.label}</span>
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-mono font-semibold text-slate-100">{row.value}</span>
                            <button
                              type="button"
                              onClick={() => copyValue(row.key, row.value)}
                              className="text-slate-400 hover:text-white transition-colors"
                              title={`Copy ${row.label}`}
                            >
                              {copiedField === row.key ? (
                                <Check className="w-3.5 h-3.5 text-emerald-400" />
                              ) : (
                                <Copy className="w-3.5 h-3.5" />
                              )}
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-white/5">
                    {va.status === "ACTIVE" && (
                      <>
                        <button
                          type="button"
                          onClick={() => setDepositTarget(va)}
                          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-emerald-600/20 border border-emerald-500/30 text-emerald-300 hover:bg-emerald-600/30 text-xs font-semibold transition-colors"
                        >
                          <ArrowDownLeft className="w-3.5 h-3.5" />
                          Simulate Inbound Deposit
                        </button>
                        <button
                          type="button"
                          onClick={() => handleStatusChange(va, "suspend")}
                          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-amber-500/10 border border-amber-500/25 text-amber-300 hover:bg-amber-500/20 text-xs font-semibold transition-colors"
                        >
                          <PauseCircle className="w-3.5 h-3.5" />
                          Suspend
                        </button>
                      </>
                    )}
                    {va.status === "SUSPENDED" && (
                      <button
                        type="button"
                        onClick={() => handleStatusChange(va, "activate")}
                        className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-blue-500/15 border border-blue-500/30 text-blue-300 hover:bg-blue-500/25 text-xs font-semibold transition-colors"
                      >
                        <PlayCircle className="w-3.5 h-3.5" />
                        Reactivate
                      </button>
                    )}
                    {va.status !== "CLOSED" && (
                      <button
                        type="button"
                        onClick={() => handleStatusChange(va, "close")}
                        className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-rose-500/10 border border-rose-500/25 text-rose-300 hover:bg-rose-500/20 text-xs font-semibold transition-colors ml-auto"
                      >
                        <XCircle className="w-3.5 h-3.5" />
                        Close
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Simulate Deposit Modal */}
        {depositTarget && (
          <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="w-full max-w-md rounded-2xl border border-white/10 bg-slate-900 p-6 space-y-5">
              <div>
                <h2 className="text-lg font-bold text-white">Simulate Inbound Bank Transfer</h2>
                <p className="text-xs text-slate-400 mt-1">
                  Credits your linked {depositTarget.currency} wallet via double-entry ledger posting.
                </p>
              </div>
              <form onSubmit={handleSimulateDeposit} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-400 mb-1.5">
                    Amount ({depositTarget.currency})
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    value={depositAmount}
                    onChange={(e) => setDepositAmount(e.target.value)}
                    className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-400 mb-1.5">Sender Name</label>
                  <input
                    type="text"
                    value={senderName}
                    onChange={(e) => setSenderName(e.target.value)}
                    className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-400 mb-1.5">Narration</label>
                  <input
                    type="text"
                    value={narration}
                    onChange={(e) => setNarration(e.target.value)}
                    className="w-full rounded-xl bg-slate-950 border border-white/10 px-3.5 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div className="flex items-center justify-end gap-3 pt-2">
                  <button
                    type="button"
                    onClick={() => setDepositTarget(null)}
                    className="px-4 py-2 rounded-xl bg-slate-800 text-xs font-semibold text-slate-300 hover:bg-slate-700"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={depositing}
                    className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-xs font-semibold text-white"
                  >
                    {depositing ? "Crediting..." : "Credit Wallet"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

