"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api } from "@/lib/api";
import { AccountBalance, TransactionRecord } from "@/types";
import { 
  Wallet, 
  ArrowUpRight, 
  ArrowDownLeft, 
  ShieldCheck, 
  CheckCircle2, 
  Copy, 
  Check, 
  RefreshCw,
  Landmark,
  ArrowLeftRight,
  ArrowRight,
  Eye,
  EyeOff,
  Sparkles,
  CreditCard,
  Search,
  Filter,
  TrendingUp,
  AlertCircle,
  PlusCircle,
  Download,
  X
} from "lucide-react";
import Link from "next/link";

export default function DashboardPage() {
  const { user, isLoading } = useAuth();
  const router = useRouter();
  const [accounts, setAccounts] = useState<AccountBalance[]>([]);
  const [selectedAccountIdx, setSelectedAccountIdx] = useState(0);
  const [transactions, setTransactions] = useState<TransactionRecord[]>([]);
  const [copied, setCopied] = useState<string | null>(null);
  const [isBalanced, setIsBalanced] = useState<boolean | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [showCardDetails, setShowCardDetails] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [filterType, setFilterType] = useState<"ALL" | "TRANSFER" | "PAYOUT" | "DEPOSIT">("ALL");
  const [depositModalOpen, setDepositModalOpen] = useState(false);
  const [depositAmount, setDepositAmount] = useState("100");
  const [depositing, setDepositing] = useState(false);
  const [depositSuccess, setDepositSuccess] = useState<string | null>(null);

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    } else if (user) {
      loadData();
    }
  }, [user, isLoading]);

  const loadData = async () => {
    setRefreshing(true);
    try {
      if (user?.id) {
        const accRes = await api.get<AccountBalance[]>(`/accounts/user/${user.id}`);
        setAccounts(accRes.data);

        // Fetch user transaction history
        try {
          const txRes = await api.get<TransactionRecord[]>(`/transactions/user/${user.id}`);
          setTransactions(txRes.data || []);
        } catch (txErr) {
          console.warn("Could not load user transactions directly", txErr);
        }
      }
      const integrityRes = await api.get<{ healthy: boolean }>("/ledger/integrity-check");
      setIsBalanced(integrityRes.data.healthy);
    } catch (e) {
      console.error("Failed to load dashboard data", e);
    } finally {
      setRefreshing(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(text);
    setTimeout(() => setCopied(null), 2000);
  };

  const handleDeposit = async (e: React.FormEvent) => {
    e.preventDefault();
    const targetAccount = accounts[selectedAccountIdx] || accounts[0];
    if (!targetAccount?.id && !targetAccount?.accountId) return;
    const accId = targetAccount.id || targetAccount.accountId;

    setDepositing(true);
    setDepositSuccess(null);
    try {
      const res = await api.post("/transactions/deposit", {
        accountId: accId,
        amount: parseFloat(depositAmount),
        currency: targetAccount.currency || "USD",
        description: "Direct wallet top-up"
      });
      setDepositSuccess(`Deposited $${parseFloat(depositAmount).toFixed(2)} successfully!`);
      setTimeout(() => {
        setDepositModalOpen(false);
        setDepositSuccess(null);
      }, 1200);
      loadData();
    } catch (err: any) {
      console.error("Deposit failed", err);
    } finally {
      setDepositing(false);
    }
  };

  const handleExportStatement = async () => {
    const targetAccount = accounts[selectedAccountIdx] || accounts[0];
    const accId = targetAccount?.id || targetAccount?.accountId;
    if (!accId) return;

    try {
      const res = await api.get(`/transactions/account/${accId}/statement`, {
        responseType: "blob"
      });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `statement-${targetAccount.accountNumber || accId}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      console.error("Failed to export statement", err);
    }
  };

  if (isLoading || !user) {
    return (
      <div className="min-h-screen bg-[#060911] flex items-center justify-center">
        <div className="flex items-center gap-3 text-slate-400 font-mono text-sm">
          <RefreshCw className="w-5 h-5 animate-spin text-blue-500" />
          <span>Synchronizing Ledger State...</span>
        </div>
      </div>
    );
  }

  const currentAccount = accounts[selectedAccountIdx] || accounts[0];
  const balance = currentAccount ? parseFloat(currentAccount.balance.toString()) : 0;
  const currency = currentAccount?.currency || "USD";

  const dailyLimit = user.dailyLimit || 1000;
  const dailyUsed = user.dailyUsedToday || 0;
  const usedPercentage = Math.min(100, (dailyUsed / dailyLimit) * 100);
  const remainingAllowance = Math.max(0, dailyLimit - dailyUsed);

  // Filter transactions
  const filteredTransactions = transactions.filter((tx) => {
    const matchesSearch = 
      (tx.reference && tx.reference.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (tx.description && tx.description.toLowerCase().includes(searchQuery.toLowerCase()));
    
    if (!matchesSearch) return false;
    if (filterType === "ALL") return true;
    if (filterType === "TRANSFER") return tx.description?.toLowerCase().includes("transfer") || tx.description?.toLowerCase().includes("p2p");
    if (filterType === "PAYOUT") return tx.description?.toLowerCase().includes("payout") || tx.description?.toLowerCase().includes("withdrawal");
    if (filterType === "DEPOSIT") return tx.description?.toLowerCase().includes("deposit") || tx.description?.toLowerCase().includes("webhook");
    return true;
  });

  return (
    <div className="min-h-screen bg-[#05070e] text-slate-100">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        
        {/* Top Header & Status */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                Welcome, {user.firstName} 👋
              </h1>
              <span className="hidden sm:inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-blue-500/10 text-blue-400 border border-blue-500/20">
                Verified Customer
              </span>
            </div>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Deterministic double-entry ledger • Real-time asset balances
            </p>
          </div>

          <div className="flex items-center gap-3">
            {/* System Status Pill */}
            <div className="flex items-center gap-2.5 px-3.5 py-2 rounded-xl border border-white/10 bg-slate-900/80 text-xs backdrop-blur-md">
              <span className="text-slate-400 font-mono text-[11px]">Ledger Invariant:</span>
              {isBalanced === null ? (
                <span className="text-slate-500 font-mono">Verifying...</span>
              ) : isBalanced ? (
                <span className="flex items-center gap-1.5 text-emerald-400 font-semibold font-mono text-[11px]">
                  <CheckCircle2 className="w-3.5 h-3.5" /> BALANCED (ΣD = ΣC)
                </span>
              ) : (
                <span className="flex items-center gap-1.5 text-rose-400 font-bold font-mono text-[11px]">
                  <AlertCircle className="w-3.5 h-3.5" /> DISCREPANCY
                </span>
              )}
            </div>

            <button
              onClick={loadData}
              disabled={refreshing}
              className="p-2.5 rounded-xl border border-white/10 bg-slate-900/80 text-slate-400 hover:text-white hover:bg-slate-800 transition-all shadow-sm"
              title="Refresh Ledger Balances"
            >
              <RefreshCw className={`w-4 h-4 ${refreshing ? "animate-spin text-blue-400" : ""}`} />
            </button>
          </div>
        </div>

        {/* Quick Action Navigation Buttons */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4">
          <button
            onClick={() => setDepositModalOpen(true)}
            className="p-4 rounded-2xl border border-white/10 bg-gradient-to-br from-blue-900/40 via-slate-900/80 to-slate-900/80 hover:border-blue-500/50 hover:from-blue-900/60 transition-all group shadow-lg shadow-blue-950/20 text-left"
          >
            <div className="h-10 w-10 rounded-xl bg-blue-600/20 text-blue-400 border border-blue-500/30 flex items-center justify-center mb-3 group-hover:scale-110 transition-transform">
              <PlusCircle className="w-5 h-5" />
            </div>
            <div className="font-bold text-sm text-white group-hover:text-blue-400 transition-colors">Fund Wallet</div>
            <div className="text-[11px] text-slate-400 mt-0.5">Instant top-up deposit</div>
          </button>

          <Link
            href="/transfers"
            className="p-4 rounded-2xl border border-white/10 bg-gradient-to-br from-indigo-900/30 via-slate-900/80 to-slate-900/80 hover:border-indigo-500/40 hover:from-indigo-900/50 transition-all group shadow-lg shadow-indigo-950/20"
          >
            <div className="h-10 w-10 rounded-xl bg-indigo-600/20 text-indigo-400 border border-indigo-500/30 flex items-center justify-center mb-3 group-hover:scale-110 transition-transform">
              <ArrowLeftRight className="w-5 h-5" />
            </div>
            <div className="font-bold text-sm text-white group-hover:text-indigo-400 transition-colors">P2P Transfer</div>
            <div className="text-[11px] text-slate-400 mt-0.5">Instant zero-fee transfer</div>
          </Link>

          <Link
            href="/exchange"
            className="p-4 rounded-2xl border border-white/10 bg-gradient-to-br from-cyan-900/30 via-slate-900/80 to-slate-900/80 hover:border-cyan-500/40 hover:from-cyan-900/50 transition-all group shadow-lg shadow-cyan-950/20"
          >
            <div className="h-10 w-10 rounded-xl bg-cyan-600/20 text-cyan-400 border border-cyan-500/30 flex items-center justify-center mb-3 group-hover:scale-110 transition-transform">
              <ArrowRight className="w-5 h-5" />
            </div>
            <div className="font-bold text-sm text-white group-hover:text-cyan-400 transition-colors">FX Swap</div>
            <div className="text-[11px] text-slate-400 mt-0.5">Interbank rate exchange</div>
          </Link>

          <Link
            href="/cards"
            className="p-4 rounded-2xl border border-white/10 bg-gradient-to-br from-purple-900/30 via-slate-900/80 to-slate-900/80 hover:border-purple-500/40 hover:from-purple-900/50 transition-all group shadow-lg shadow-purple-950/20"
          >
            <div className="h-10 w-10 rounded-xl bg-purple-600/20 text-purple-400 border border-purple-500/30 flex items-center justify-center mb-3 group-hover:scale-110 transition-transform">
              <CreditCard className="w-5 h-5" />
            </div>
            <div className="font-bold text-sm text-white group-hover:text-purple-400 transition-colors">Virtual Cards</div>
            <div className="text-[11px] text-slate-400 mt-0.5">Debit controls & limits</div>
          </Link>

          <Link
            href="/payouts"
            className="p-4 rounded-2xl border border-white/10 bg-gradient-to-br from-emerald-900/30 via-slate-900/80 to-slate-900/80 hover:border-emerald-500/40 hover:from-emerald-900/50 transition-all group shadow-lg shadow-emerald-950/20"
          >
            <div className="h-10 w-10 rounded-xl bg-emerald-600/20 text-emerald-400 border border-emerald-500/30 flex items-center justify-center mb-3 group-hover:scale-110 transition-transform">
              <Landmark className="w-5 h-5" />
            </div>
            <div className="font-bold text-sm text-white group-hover:text-emerald-400 transition-colors">Bank Payout</div>
            <div className="text-[11px] text-slate-400 mt-0.5">Withdraw to external bank</div>
          </Link>

          <Link
            href="/kyc"
            className="p-4 rounded-2xl border border-white/10 bg-gradient-to-br from-amber-900/20 via-slate-900/80 to-slate-900/80 hover:border-amber-500/40 hover:from-amber-900/40 transition-all group shadow-lg shadow-amber-950/20"
          >
            <div className="h-10 w-10 rounded-xl bg-amber-600/20 text-amber-400 border border-amber-500/30 flex items-center justify-center mb-3 group-hover:scale-110 transition-transform">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div className="font-bold text-sm text-white group-hover:text-amber-400 transition-colors">Upgrade Limits</div>
            <div className="text-[11px] text-slate-400 mt-0.5">Tier {user.kycTier} • Max ${dailyLimit.toLocaleString()}</div>
          </Link>
        </div>

        {/* Hero Section: Virtual Debit Card + Account Analytics */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
          
          {/* Metallic / Holographic Virtual Card */}
          <div className="lg:col-span-5 flex flex-col">
            <div className="relative rounded-3xl p-6 sm:p-7 bg-gradient-to-tr from-slate-900 via-slate-950 to-blue-950/90 border border-white/15 shadow-2xl shadow-blue-950/50 overflow-hidden flex flex-col justify-between min-h-[250px] sm:min-h-[270px]">
              
              {/* Card Texture Overlays */}
              <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-br from-blue-500/15 via-indigo-500/10 to-transparent rounded-full blur-2xl pointer-events-none"></div>
              <div className="absolute -bottom-10 -left-10 w-48 h-48 bg-cyan-500/10 rounded-full blur-xl pointer-events-none"></div>

              {/* Card Header: Chip & Contactless */}
              <div className="flex items-center justify-between relative z-10">
                <div className="flex items-center gap-3">
                  {/* Metallic Chip */}
                  <div className="h-8 w-11 rounded-lg bg-gradient-to-br from-amber-300 via-amber-400 to-amber-600 p-[1.5px] shadow-sm">
                    <div className="h-full w-full bg-amber-400/30 rounded-[6px] border border-amber-200/50 flex flex-col justify-between p-1">
                      <div className="h-px bg-amber-900/40 w-full"></div>
                      <div className="h-px bg-amber-900/40 w-full"></div>
                    </div>
                  </div>
                  {/* Contactless waves icon */}
                  <div className="text-slate-400 font-mono text-sm tracking-widest rotate-90">
                    )))
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <span className="px-2.5 py-1 rounded-full bg-blue-600/30 text-blue-300 text-[10px] font-mono font-bold tracking-wider uppercase border border-blue-400/30">
                    {currency} Primary
                  </span>
                  <button 
                    onClick={() => setShowCardDetails(!showCardDetails)}
                    className="p-1 text-slate-400 hover:text-white transition-colors"
                    title={showCardDetails ? "Hide Card Details" : "Show Card Details"}
                  >
                    {showCardDetails ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* Card Number / Account ID */}
              <div className="my-4 relative z-10">
                <div className="text-[11px] font-mono text-slate-400 uppercase tracking-widest">Account Identification</div>
                <div className="text-lg sm:text-xl font-mono font-bold tracking-wider text-slate-100 mt-1 flex items-center gap-2">
                  {showCardDetails && currentAccount?.id ? (
                    <span className="truncate max-w-[260px]">{currentAccount.id}</span>
                  ) : (
                    <span>•••• •••• •••• {currentAccount?.id ? currentAccount.id.slice(-4) : "8842"}</span>
                  )}
                  {currentAccount?.id && (
                    <button
                      onClick={() => currentAccount.id && copyToClipboard(currentAccount.id)}
                      className="p-1 hover:text-blue-400 text-slate-400 transition-colors"
                      title="Copy Full UUID"
                    >
                      {copied === currentAccount.id ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                    </button>
                  )}
                </div>
              </div>

              {/* Card Footer: Holder, Exp, CVV & Balance */}
              <div className="flex items-end justify-between relative z-10 border-t border-white/10 pt-3">
                <div>
                  <div className="text-[9px] font-mono uppercase text-slate-400">Cardholder</div>
                  <div className="text-xs font-semibold uppercase text-slate-200">
                    {user.firstName} {user.lastName}
                  </div>
                </div>

                <div className="text-center">
                  <div className="text-[9px] font-mono uppercase text-slate-400">Expires</div>
                  <div className="text-xs font-mono text-slate-200">11/29</div>
                </div>

                <div className="text-center">
                  <div className="text-[9px] font-mono uppercase text-slate-400">CVV</div>
                  <div className="text-xs font-mono text-slate-200">
                    {showCardDetails ? "842" : "•••"}
                  </div>
                </div>

                <div className="text-right">
                  <div className="text-[9px] font-mono uppercase text-slate-400">Available</div>
                  <div className="text-sm font-black font-mono text-emerald-400">
                    ${balance.toLocaleString("en-US", { minimumFractionDigits: 2 })}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Account Analytics & Limits Progress */}
          <div className="lg:col-span-7 flex flex-col justify-between gap-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              
              {/* Total Balance Card */}
              <div className="p-6 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl relative overflow-hidden">
                <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1 flex items-center justify-between">
                  <span>Total Net Balance</span>
                  <Wallet className="w-4 h-4 text-blue-400" />
                </div>
                <div className="text-3xl sm:text-4xl font-black text-white font-mono tracking-tight mt-2">
                  ${balance.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </div>
                <div className="flex items-center gap-2 text-xs text-emerald-400 font-mono mt-3">
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded bg-emerald-500/10 border border-emerald-500/20 text-[10px]">
                    ACID Secured
                  </span>
                  <span>Currency: {currency}</span>
                </div>
              </div>

              {/* KYC Daily Limit Card */}
              <div className="p-6 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl relative overflow-hidden">
                <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1 flex items-center justify-between">
                  <span>Daily Transfer Allowance</span>
                  <ShieldCheck className="w-4 h-4 text-emerald-400" />
                </div>
                <div className="text-3xl sm:text-4xl font-black text-white font-mono tracking-tight mt-2">
                  ${remainingAllowance.toLocaleString("en-US", { minimumFractionDigits: 2 })}
                </div>
                <div className="text-xs text-slate-400 font-mono mt-1">
                  Remaining of ${dailyLimit.toLocaleString()} limit
                </div>

                {/* Progress Bar */}
                <div className="w-full bg-slate-800 rounded-full h-2 mt-3 overflow-hidden border border-white/5">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${
                      usedPercentage > 85 ? "bg-rose-500" : usedPercentage > 50 ? "bg-amber-500" : "bg-blue-500"
                    }`}
                    style={{ width: `${usedPercentage}%` }}
                  ></div>
                </div>
              </div>
            </div>

            {/* Quick Beneficiary / Recipient Shortcuts */}
            <div className="p-5 rounded-3xl border border-white/10 bg-slate-900/50 backdrop-blur-md">
              <div className="flex items-center justify-between mb-3">
                <div className="text-xs font-bold text-slate-300 uppercase tracking-wider">Fast Transfer Shortcuts</div>
                <Link href="/transfers" className="text-xs text-blue-400 hover:text-blue-300 font-medium flex items-center gap-1">
                  New Transfer <ArrowRight className="w-3.5 h-3.5" />
                </Link>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                {[
                  { name: "Treasury Clearing", code: "ACC-SYS-CLEARING", badge: "Asset" },
                  { name: "Merchant Settlement", code: "ACC-PAY-STRI", badge: "Clearing" },
                  { name: "Exchange Reserve", code: "ACC-RES-USD", badge: "Reserve" },
                  { name: "Platform Fees", code: "ACC-REV-FEE", badge: "Revenue" },
                ].map((recipient) => (
                  <button
                    key={recipient.name}
                    onClick={() => router.push(`/transfers?recipient=${recipient.code}`)}
                    className="p-3 rounded-2xl border border-white/5 bg-slate-950/60 hover:bg-slate-800/80 hover:border-blue-500/30 text-left transition-all"
                  >
                    <div className="text-xs font-semibold text-slate-200 truncate">{recipient.name}</div>
                    <div className="text-[10px] font-mono text-slate-400 truncate mt-0.5">{recipient.code}</div>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Transaction History Section with Search & Filter */}
        <div className="p-6 sm:p-8 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl shadow-xl space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <h2 className="text-lg font-bold text-white tracking-tight">Recent Ledger Postings</h2>
              <p className="text-xs text-slate-400">Cryptographically verifiable immutable transaction records</p>
            </div>

            {/* Search & Filter Controls */}
            <div className="flex flex-wrap items-center gap-3">
              <button
                onClick={handleExportStatement}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-950 hover:bg-slate-800 border border-slate-700/80 text-xs text-slate-300 hover:text-white transition-all font-mono"
                title="Download CSV Statement"
              >
                <Download className="w-3.5 h-3.5 text-blue-400" />
                <span>Export CSV</span>
              </button>

              <div className="relative">
                <Search className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
                <input
                  type="text"
                  placeholder="Search reference or note..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-9 pr-3.5 py-1.5 rounded-xl bg-slate-950 border border-slate-700/80 text-xs text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-blue-500 w-48 sm:w-60 font-mono"
                />
              </div>

              <div className="flex items-center rounded-xl bg-slate-950 p-1 border border-white/5">
                {(["ALL", "TRANSFER", "PAYOUT", "DEPOSIT"] as const).map((tab) => (
                  <button
                    key={tab}
                    onClick={() => setFilterType(tab)}
                    className={`px-3 py-1 rounded-lg text-xs font-medium transition-all ${
                      filterType === tab
                        ? "bg-blue-600 text-white font-semibold shadow-sm"
                        : "text-slate-400 hover:text-white"
                    }`}
                  >
                    {tab}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Transaction Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-white/5 text-slate-400 font-mono uppercase text-[10px] tracking-wider">
                  <th className="py-3 px-3">Type & Reference</th>
                  <th className="py-3 px-3">Description</th>
                  <th className="py-3 px-3">Status</th>
                  <th className="py-3 px-3">Timestamp</th>
                  <th className="py-3 px-3 text-right">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {filteredTransactions.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-12 text-center text-slate-500">
                      <div className="flex flex-col items-center justify-center space-y-2">
                        <Wallet className="w-8 h-8 text-slate-600" />
                        <span className="text-sm font-medium">No transactions recorded yet</span>
                        <span className="text-xs text-slate-600">Post a P2P transfer or request a payout to test the double-entry ledger.</span>
                      </div>
                    </td>
                  </tr>
                ) : (
                  filteredTransactions.map((tx) => {
                    const isDebit = tx.amount ? parseFloat(tx.amount.toString()) < 0 : false;
                    return (
                      <tr key={tx.id || tx.reference} className="hover:bg-slate-800/30 transition-colors">
                        <td className="py-3 px-3 font-mono font-medium text-slate-200">
                          <div className="flex items-center gap-2">
                            <div className={`p-1 rounded-md ${isDebit ? "bg-rose-500/10 text-rose-400" : "bg-emerald-500/10 text-emerald-400"}`}>
                              {isDebit ? <ArrowUpRight className="w-3.5 h-3.5" /> : <ArrowDownLeft className="w-3.5 h-3.5" />}
                            </div>
                            <span className="truncate max-w-[130px]">{tx.reference}</span>
                            <button
                              onClick={() => copyToClipboard(tx.reference)}
                              className="text-slate-500 hover:text-slate-300"
                              title="Copy Reference"
                            >
                              <Copy className="w-3 h-3" />
                            </button>
                          </div>
                        </td>
                        <td className="py-3 px-3 text-slate-300 max-w-[200px] truncate">
                          {tx.description || "Ledger Entry Posting"}
                        </td>
                        <td className="py-3 px-3">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold tracking-wide ${
                            tx.status === "COMPLETED" 
                              ? "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30"
                              : tx.status === "PENDING"
                              ? "bg-amber-500/15 text-amber-400 border border-amber-500/30"
                              : "bg-slate-700/50 text-slate-400"
                          }`}>
                            {tx.status || "COMPLETED"}
                          </span>
                        </td>
                        <td className="py-3 px-3 text-slate-400 font-mono text-[11px]">
                          {tx.createdAt ? new Date(tx.createdAt).toLocaleDateString() : "Just now"}
                        </td>
                        <td className={`py-3 px-3 font-mono text-right font-bold ${isDebit ? "text-rose-400" : "text-emerald-400"}`}>
                          {isDebit ? "-" : "+"}
                          ${Math.abs(parseFloat(tx.amount?.toString() || "0")).toLocaleString("en-US", { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Deposit / Fund Wallet Modal */}
        {depositModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-white/15 rounded-3xl p-6 sm:p-7 max-w-md w-full shadow-2xl relative animate-in fade-in zoom-in-95 duration-200">
              <div className="flex items-center justify-between pb-4 border-b border-white/10">
                <div className="flex items-center gap-3">
                  <div className="h-10 w-10 rounded-xl bg-blue-600/20 text-blue-400 border border-blue-500/30 flex items-center justify-center">
                    <PlusCircle className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-bold text-white text-base">Top-up Wallet</h3>
                    <p className="text-xs text-slate-400">Direct ledger clearing deposit</p>
                  </div>
                </div>
                <button
                  onClick={() => setDepositModalOpen(false)}
                  className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleDeposit} className="mt-5 space-y-4">
                <div>
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider block mb-1.5">
                    Target Account
                  </label>
                  <div className="p-3 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between">
                    <div>
                      <div className="text-xs font-bold text-white">{currentAccount?.accountName || "Primary Wallet"}</div>
                      <div className="text-[11px] font-mono text-slate-400">{currentAccount?.accountNumber || currentAccount?.id}</div>
                    </div>
                    <span className="px-2 py-0.5 rounded bg-blue-600/20 text-blue-300 font-mono text-[10px] font-bold">
                      {currency}
                    </span>
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider block mb-1.5">
                    Amount ({currency})
                  </label>
                  <div className="relative">
                    <span className="absolute left-3.5 top-3 text-slate-500 font-mono font-bold">$</span>
                    <input
                      type="number"
                      step="0.01"
                      min="1.00"
                      max="100000.00"
                      required
                      value={depositAmount}
                      onChange={(e) => setDepositAmount(e.target.value)}
                      className="w-full pl-8 pr-4 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono font-bold text-lg focus:outline-none focus:border-blue-500"
                      placeholder="100.00"
                    />
                  </div>
                </div>

                {/* Preset quick buttons */}
                <div className="grid grid-cols-4 gap-2">
                  {["50", "100", "250", "500"].map((preset) => (
                    <button
                      type="button"
                      key={preset}
                      onClick={() => setDepositAmount(preset)}
                      className={`py-1.5 rounded-lg border text-xs font-mono font-semibold transition-all ${
                        depositAmount === preset
                          ? "bg-blue-600/30 border-blue-500 text-blue-300"
                          : "border-slate-800 bg-slate-950/60 text-slate-400 hover:text-white hover:border-slate-700"
                      }`}
                    >
                      +${preset}
                    </button>
                  ))}
                </div>

                {depositSuccess && (
                  <div className="p-3 rounded-xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 text-xs font-medium flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>{depositSuccess}</span>
                  </div>
                )}

                <div className="pt-2 flex items-center justify-end gap-3">
                  <button
                    type="button"
                    onClick={() => setDepositModalOpen(false)}
                    className="px-4 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:text-white transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={depositing || !depositAmount || parseFloat(depositAmount) <= 0}
                    className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold shadow-lg shadow-blue-600/30 flex items-center gap-2 disabled:opacity-50 transition-all"
                  >
                    {depositing ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Posting Ledger Entry...</span>
                      </>
                    ) : (
                      <>
                        <PlusCircle className="w-4 h-4" />
                        <span>Confirm Deposit</span>
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
