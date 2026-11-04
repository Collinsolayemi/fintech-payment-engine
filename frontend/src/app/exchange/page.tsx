"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api } from "@/lib/api";
import { AccountBalance, FxRate, FxQuote, FxExchangeResult } from "@/types";
import { 
  ArrowLeftRight, 
  ArrowRight, 
  RefreshCw, 
  CheckCircle2, 
  AlertCircle, 
  Clock, 
  PlusCircle, 
  Sparkles, 
  TrendingUp, 
  Wallet,
  ShieldCheck,
  Check,
  Zap,
  Globe2,
  X
} from "lucide-react";

export default function ExchangePage() {
  const { user, isLoading } = useAuth();
  const router = useRouter();

  const [accounts, setAccounts] = useState<AccountBalance[]>([]);
  const [rates, setRates] = useState<FxRate[]>([]);
  const [selectedSourceId, setSelectedSourceId] = useState<string>("");
  const [selectedTargetId, setSelectedTargetId] = useState<string>("");
  const [sourceAmount, setSourceAmount] = useState<string>("100.00");
  const [quote, setQuote] = useState<FxQuote | null>(null);
  const [countdown, setCountdown] = useState<number>(0);
  const [loadingQuote, setLoadingQuote] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [successResult, setSuccessResult] = useState<FxExchangeResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // New Wallet Provisioning Modal
  const [walletModalOpen, setWalletModalOpen] = useState(false);
  const [newCurrency, setNewCurrency] = useState("EUR");
  const [creatingWallet, setCreatingWallet] = useState(false);
  const [walletSuccess, setWalletSuccess] = useState<string | null>(null);

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    } else if (user) {
      loadData();
    }
  }, [user, isLoading]);

  // Countdown timer for guaranteed FX quote lock
  useEffect(() => {
    if (!quote || countdown <= 0) return;
    const interval = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          setQuote(null);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [quote, countdown]);

  const loadData = async () => {
    try {
      if (user?.id) {
        const accRes = await api.get<AccountBalance[]>(`/accounts/user/${user.id}`);
        setAccounts(accRes.data || []);
        if (accRes.data?.length > 0 && !selectedSourceId) {
          setSelectedSourceId(accRes.data[0].id || accRes.data[0].accountId);
        }
        if (accRes.data?.length > 1 && !selectedTargetId) {
          setSelectedTargetId(accRes.data[1].id || accRes.data[1].accountId);
        }
      }
      const ratesRes = await api.get<FxRate[]>("/fx/rates?base=USD");
      setRates(ratesRes.data || []);
    } catch (e) {
      console.error("Failed to load FX data", e);
    }
  };

  const handleSwapCurrencies = () => {
    const temp = selectedSourceId;
    setSelectedSourceId(selectedTargetId);
    setSelectedTargetId(temp);
    setQuote(null);
    setErrorMessage(null);
  };

  const handleRequestQuote = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessResult(null);

    const sourceAcc = accounts.find((a) => (a.id || a.accountId) === selectedSourceId);
    const targetAcc = accounts.find((a) => (a.id || a.accountId) === selectedTargetId);

    if (!sourceAcc || !targetAcc) {
      setErrorMessage("Please select valid source and destination wallets");
      return;
    }

    if (sourceAcc.currency === targetAcc.currency) {
      setErrorMessage("Source and target wallets must have different currencies");
      return;
    }

    setLoadingQuote(true);
    try {
      const res = await api.post<FxQuote>("/fx/quote", {
        sourceCurrency: sourceAcc.currency,
        targetCurrency: targetAcc.currency,
        sourceAmount: parseFloat(sourceAmount),
      });
      setQuote(res.data);
      setCountdown(res.data.expiresInSeconds || 60);
    } catch (err: any) {
      setErrorMessage(err.response?.data?.detail || "Failed to generate rate quote");
    } finally {
      setLoadingQuote(false);
    }
  };

  const handleExecuteExchange = async () => {
    if (!quote) return;
    setExecuting(true);
    setErrorMessage(null);

    try {
      const idempotencyKey = crypto.randomUUID();
      const res = await api.post<FxExchangeResult>(
        "/fx/execute",
        {
          quoteId: quote.quoteId,
          sourceAccountId: selectedSourceId,
          targetAccountId: selectedTargetId,
        },
        {
          headers: { "Idempotency-Key": idempotencyKey },
        }
      );
      setSuccessResult(res.data);
      setQuote(null);
      loadData();
    } catch (err: any) {
      setErrorMessage(err.response?.data?.detail || "Failed to execute exchange");
    } finally {
      setExecuting(false);
    }
  };

  const handleCreateWallet = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreatingWallet(true);
    setWalletSuccess(null);
    try {
      await api.post("/accounts/wallet", {
        currency: newCurrency,
        accountName: `${newCurrency} Primary Wallet`,
      });
      setWalletSuccess(`Provisioned ${newCurrency} wallet successfully!`);
      setTimeout(() => {
        setWalletModalOpen(false);
        setWalletSuccess(null);
      }, 1200);
      loadData();
    } catch (err: any) {
      setErrorMessage(err.response?.data?.detail || "Failed to provision wallet");
    } finally {
      setCreatingWallet(false);
    }
  };

  const sourceAccount = accounts.find((a) => (a.id || a.accountId) === selectedSourceId);
  const targetAccount = accounts.find((a) => (a.id || a.accountId) === selectedTargetId);

  return (
    <div className="min-h-screen bg-[#060911] text-slate-100 flex flex-col font-sans selection:bg-blue-600 selection:text-white">
      <Navbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        {/* Header with Multi-Currency Actions */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/20 text-blue-400 text-xs font-mono font-medium mb-2">
              <Globe2 className="w-3.5 h-3.5 animate-spin-slow" />
              <span>Institutional Multi-Currency Foreign Exchange</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
              FX Exchange & Multi-Currency Treasury
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Guaranteed institutional rate locks, double-entry cross-currency settlement, and zero hidden markups.
            </p>
          </div>

          <button
            onClick={() => setWalletModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold shadow-lg shadow-blue-600/25 transition-all self-start sm:self-auto"
          >
            <PlusCircle className="w-4 h-4" />
            <span>Open Currency Wallet</span>
          </button>
        </div>

        {/* Live Market Rates Ticker Bar */}
        <div className="p-4 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl shadow-xl overflow-hidden">
          <div className="flex items-center gap-2 text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">
            <TrendingUp className="w-4 h-4 text-emerald-400" />
            <span>Live Interbank Reference Rates (Base USD)</span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3">
            {rates.map((rate) => (
              <div
                key={rate.targetCurrency}
                className="p-3 rounded-2xl bg-slate-950/70 border border-white/5 flex flex-col justify-between hover:border-blue-500/30 transition-all"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-white font-mono">{rate.sourceCurrency}/{rate.targetCurrency}</span>
                  <span className="text-[10px] text-emerald-400 font-mono font-bold">+0.12%</span>
                </div>
                <div className="text-base font-black text-white font-mono mt-1">
                  {parseFloat(rate.rate.toString()).toFixed(4)}
                </div>
                <div className="text-[10px] text-slate-500 font-mono mt-0.5">
                  1 {rate.targetCurrency} = {parseFloat(rate.inverseRate.toString()).toFixed(4)} USD
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Main FX Converter Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* FX Converter Card */}
          <div className="lg:col-span-7 rounded-3xl border border-white/10 bg-slate-900/70 backdrop-blur-2xl p-6 sm:p-8 shadow-2xl space-y-6 relative overflow-hidden">
            <div className="flex items-center justify-between border-b border-white/10 pb-4">
              <div className="flex items-center gap-3">
                <div className="h-10 w-10 rounded-xl bg-blue-600/20 text-blue-400 border border-blue-500/30 flex items-center justify-center">
                  <ArrowLeftRight className="w-5 h-5" />
                </div>
                <div>
                  <h2 className="font-bold text-white text-base">Instant Currency Swap</h2>
                  <p className="text-xs text-slate-400">Lock guaranteed rate for 60 seconds</p>
                </div>
              </div>

              {quote && countdown > 0 && (
                <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-500/15 border border-amber-500/30 text-amber-400 text-xs font-mono font-bold animate-pulse">
                  <Clock className="w-3.5 h-3.5" />
                  <span>Rate locked: {countdown}s</span>
                </div>
              )}
            </div>

            {errorMessage && (
              <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMessage}</span>
              </div>
            )}

            {successResult && (
              <div className="p-4 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs space-y-1">
                <div className="flex items-center gap-2 font-bold text-sm">
                  <CheckCircle2 className="w-4 h-4" />
                  <span>Currency Swap Executed Successfully!</span>
                </div>
                <div className="text-[11px] font-mono text-emerald-300">
                  Ref: {successResult.transactionReference} • Converted {successResult.sourceAmount} {successResult.sourceCurrency} → {successResult.targetAmount} {successResult.targetCurrency}
                </div>
              </div>
            )}

            <form onSubmit={handleRequestQuote} className="space-y-5">
              {/* Source Account Field */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
                    From Wallet (Debit)
                  </label>
                  {sourceAccount && (
                    <span className="text-xs font-mono text-slate-400">
                      Balance: <span className="text-emerald-400 font-bold">${parseFloat(sourceAccount.balance.toString()).toFixed(2)}</span> {sourceAccount.currency}
                    </span>
                  )}
                </div>

                <select
                  value={selectedSourceId}
                  onChange={(e) => {
                    setSelectedSourceId(e.target.value);
                    setQuote(null);
                  }}
                  className="w-full px-4 py-3 rounded-2xl bg-slate-950 border border-slate-700/80 text-white font-mono text-sm focus:outline-none focus:border-blue-500"
                >
                  {accounts.map((acc) => (
                    <option key={acc.id || acc.accountId} value={acc.id || acc.accountId}>
                      {acc.accountName} ({acc.accountNumber}) — {acc.currency} (Bal: {parseFloat(acc.balance.toString()).toFixed(2)})
                    </option>
                  ))}
                </select>
              </div>

              {/* Amount Input */}
              <div>
                <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider block mb-2">
                  You Pay Amount ({sourceAccount?.currency || "USD"})
                </label>
                <div className="relative">
                  <span className="absolute left-4 top-3 text-slate-500 font-mono font-bold">$</span>
                  <input
                    type="number"
                    step="0.01"
                    min="1.00"
                    max="100000.00"
                    required
                    value={sourceAmount}
                    onChange={(e) => {
                      setSourceAmount(e.target.value);
                      setQuote(null);
                    }}
                    className="w-full pl-9 pr-4 py-3 rounded-2xl bg-slate-950 border border-slate-700 text-white font-mono font-bold text-lg focus:outline-none focus:border-blue-500"
                    placeholder="100.00"
                  />
                </div>
              </div>

              {/* Swap Button Divider */}
              <div className="flex justify-center -my-2 relative z-10">
                <button
                  type="button"
                  onClick={handleSwapCurrencies}
                  className="h-10 w-10 rounded-full bg-slate-800 hover:bg-slate-700 border border-white/10 flex items-center justify-center text-blue-400 hover:text-white transition-all shadow-lg hover:scale-110"
                  title="Swap Source and Destination"
                >
                  <ArrowLeftRight className="w-4 h-4 rotate-90" />
                </button>
              </div>

              {/* Target Account Field */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
                    To Wallet (Credit)
                  </label>
                  {targetAccount && (
                    <span className="text-xs font-mono text-slate-400">
                      Balance: <span className="text-emerald-400 font-bold">${parseFloat(targetAccount.balance.toString()).toFixed(2)}</span> {targetAccount.currency}
                    </span>
                  )}
                </div>

                <select
                  value={selectedTargetId}
                  onChange={(e) => {
                    setSelectedTargetId(e.target.value);
                    setQuote(null);
                  }}
                  className="w-full px-4 py-3 rounded-2xl bg-slate-950 border border-slate-700/80 text-white font-mono text-sm focus:outline-none focus:border-blue-500"
                >
                  {accounts.map((acc) => (
                    <option key={acc.id || acc.accountId} value={acc.id || acc.accountId}>
                      {acc.accountName} ({acc.accountNumber}) — {acc.currency} (Bal: {parseFloat(acc.balance.toString()).toFixed(2)})
                    </option>
                  ))}
                </select>
              </div>

              {/* Quote Breakdown Display */}
              {quote && (
                <div className="p-4 rounded-2xl bg-blue-950/30 border border-blue-500/30 space-y-2.5 animate-in fade-in duration-200">
                  <div className="flex items-center justify-between text-xs text-slate-400">
                    <span>Guaranteed Conversion Rate:</span>
                    <span className="font-mono font-bold text-white">
                      1 {quote.sourceCurrency} = {parseFloat(quote.exchangeRate.toString()).toFixed(6)} {quote.targetCurrency}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-xs text-slate-400">
                    <span>Institutional Processing Fee (0.10%):</span>
                    <span className="font-mono text-slate-300">${parseFloat(quote.fee.toString()).toFixed(2)} {quote.sourceCurrency}</span>
                  </div>
                  <div className="h-px bg-white/10 my-1"></div>
                  <div className="flex items-center justify-between text-sm font-bold text-white">
                    <span>You Receive (Credited into Wallet):</span>
                    <span className="font-mono text-emerald-400 text-lg">
                      {parseFloat(quote.targetAmount.toString()).toFixed(2)} {quote.targetCurrency}
                    </span>
                  </div>
                </div>
              )}

              {/* Action Buttons */}
              <div className="pt-2">
                {!quote ? (
                  <button
                    type="submit"
                    disabled={loadingQuote}
                    className="w-full py-3.5 rounded-2xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-sm shadow-xl shadow-blue-600/30 flex items-center justify-center gap-2 transition-all disabled:opacity-50"
                  >
                    {loadingQuote ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Querying Interbank Liquidity...</span>
                      </>
                    ) : (
                      <>
                        <Sparkles className="w-4 h-4 text-cyan-300" />
                        <span>Get Guaranteed Quote (60s Lock)</span>
                      </>
                    )}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleExecuteExchange}
                    disabled={executing || countdown <= 0}
                    className="w-full py-3.5 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-bold text-sm shadow-xl shadow-emerald-600/30 flex items-center justify-center gap-2 transition-all disabled:opacity-50"
                  >
                    {executing ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Posting Multi-Currency Ledger Entries...</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-4 h-4" />
                        <span>Confirm & Execute Swap ({countdown}s left)</span>
                      </>
                    )}
                  </button>
                )}
              </div>
            </form>
          </div>

          {/* Right Column: Multi-Currency Wallets Overview */}
          <div className="lg:col-span-5 space-y-6">
            <div className="p-6 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl shadow-xl space-y-4">
              <div className="flex items-center justify-between">
                <div className="text-xs font-bold text-slate-300 uppercase tracking-wider flex items-center gap-2">
                  <Wallet className="w-4 h-4 text-blue-400" />
                  <span>Your Multi-Currency Wallets</span>
                </div>
                <button
                  onClick={() => setWalletModalOpen(true)}
                  className="text-xs text-blue-400 hover:text-blue-300 font-medium flex items-center gap-1"
                >
                  <PlusCircle className="w-3.5 h-3.5" />
                  <span>Add Wallet</span>
                </button>
              </div>

              <div className="space-y-3">
                {accounts.map((acc) => (
                  <div
                    key={acc.id || acc.accountId}
                    className="p-4 rounded-2xl bg-slate-950/80 border border-white/5 flex items-center justify-between hover:border-slate-700 transition-all"
                  >
                    <div className="flex items-center gap-3">
                      <div className="h-9 w-9 rounded-xl bg-blue-600/20 text-blue-300 border border-blue-500/30 flex items-center justify-center font-bold font-mono text-xs">
                        {acc.currency}
                      </div>
                      <div>
                        <div className="text-xs font-bold text-white">{acc.accountName}</div>
                        <div className="text-[10px] font-mono text-slate-400">{acc.accountNumber}</div>
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-sm font-bold font-mono text-white">
                        {parseFloat(acc.balance.toString()).toLocaleString("en-US", { minimumFractionDigits: 2 })} {acc.currency}
                      </div>
                      <span className="text-[10px] font-mono text-emerald-400 uppercase tracking-wider font-semibold">
                        ACID Settled
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Security Guarantee Box */}
            <div className="p-6 rounded-3xl border border-white/10 bg-gradient-to-br from-indigo-950/30 via-slate-900/60 to-slate-900/60 backdrop-blur-xl space-y-3">
              <div className="flex items-center gap-2.5 text-xs font-bold text-indigo-300 uppercase tracking-wider">
                <ShieldCheck className="w-4 h-4 text-indigo-400" />
                <span>ACID Atomic Clearing</span>
              </div>
              <p className="text-xs text-slate-400 leading-relaxed">
                All foreign exchange conversions execute simultaneously across multi-currency clearing asset accounts and user liability wallets with zero slippage.
              </p>
            </div>
          </div>
        </div>

        {/* Open Currency Wallet Modal */}
        {walletModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-white/15 rounded-3xl p-6 sm:p-7 max-w-md w-full shadow-2xl relative animate-in fade-in zoom-in-95 duration-200">
              <div className="flex items-center justify-between pb-4 border-b border-white/10">
                <div className="flex items-center gap-3">
                  <div className="h-10 w-10 rounded-xl bg-blue-600/20 text-blue-400 border border-blue-500/30 flex items-center justify-center">
                    <PlusCircle className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-bold text-white text-base">Provision Currency Wallet</h3>
                    <p className="text-xs text-slate-400">Open a dedicated multi-currency IBAN account</p>
                  </div>
                </div>
                <button
                  onClick={() => setWalletModalOpen(false)}
                  className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleCreateWallet} className="mt-5 space-y-4">
                <div>
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider block mb-2">
                    Select Currency
                  </label>
                  <div className="grid grid-cols-3 gap-2.5">
                    {[
                      { code: "EUR", name: "Euro", symbol: "€" },
                      { code: "GBP", name: "British Pound", symbol: "£" },
                      { code: "CAD", name: "Canadian Dollar", symbol: "C$" },
                      { code: "AUD", name: "Australian Dollar", symbol: "A$" },
                      { code: "JPY", name: "Japanese Yen", symbol: "¥" },
                      { code: "NGN", name: "Nigerian Naira", symbol: "₦" },
                    ].map((c) => (
                      <button
                        type="button"
                        key={c.code}
                        onClick={() => setNewCurrency(c.code)}
                        className={`p-3 rounded-2xl border text-left transition-all ${
                          newCurrency === c.code
                            ? "bg-blue-600/25 border-blue-500 text-white"
                            : "bg-slate-950 border-slate-800 text-slate-400 hover:border-slate-700"
                        }`}
                      >
                        <div className="font-bold font-mono text-sm text-white">{c.code}</div>
                        <div className="text-[10px] text-slate-400 truncate mt-0.5">{c.name}</div>
                      </button>
                    ))}
                  </div>
                </div>

                {walletSuccess && (
                  <div className="p-3 rounded-xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 text-xs font-medium flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>{walletSuccess}</span>
                  </div>
                )}

                <div className="pt-3 flex items-center justify-end gap-3">
                  <button
                    type="button"
                    onClick={() => setWalletModalOpen(false)}
                    className="px-4 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:text-white transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={creatingWallet}
                    className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold shadow-lg shadow-blue-600/30 flex items-center gap-2 disabled:opacity-50 transition-all"
                  >
                    {creatingWallet ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Provisioning Wallet...</span>
                      </>
                    ) : (
                      <>
                        <PlusCircle className="w-4 h-4" />
                        <span>Create {newCurrency} Wallet</span>
                      </>
                    )}
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
