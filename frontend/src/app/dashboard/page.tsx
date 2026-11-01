"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api } from "@/lib/api";
import { AccountBalance } from "@/types";
import { Wallet, ArrowUpRight, ArrowDownLeft, ShieldCheck, CheckCircle2, Copy, Check, RefreshCw } from "lucide-react";
import Link from "next/link";

export default function DashboardPage() {
  const { user, isLoading } = useAuth();
  const router = useRouter();
  const [accounts, setAccounts] = useState<AccountBalance[]>([]);
  const [copied, setCopied] = useState<string | null>(null);
  const [isBalanced, setIsBalanced] = useState<boolean | null>(null);
  const [refreshing, setRefreshing] = useState(false);

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

  if (isLoading || !user) {
    return <div className="min-h-screen bg-slate-950 flex items-center justify-center text-slate-400">Loading...</div>;
  }

  const primaryAccount = accounts[0];
  const usedPercentage = Math.min(
    100,
    user.dailyLimit > 0 ? (user.dailyUsedToday / user.dailyLimit) * 100 : 0
  );

  return (
    <div className="min-h-screen bg-slate-950">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        {/* Welcome & Integrity Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-white tracking-tight">
              Hello, {user.firstName} 👋
            </h1>
            <p className="text-sm text-slate-400">Welcome to your core financial wallet dashboard.</p>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-slate-800 bg-slate-900 text-xs font-medium">
              <span className="text-slate-400">Platform Ledger:</span>
              {isBalanced === null ? (
                <span className="text-slate-500">Checking...</span>
              ) : isBalanced ? (
                <span className="flex items-center gap-1 text-emerald-400">
                  <CheckCircle2 className="w-3.5 h-3.5" /> Balanced
                </span>
              ) : (
                <span className="text-rose-400">Unbalanced Alert</span>
              )}
            </div>

            <button
              onClick={loadData}
              disabled={refreshing}
              className="p-2 rounded-lg border border-slate-800 bg-slate-900 text-slate-400 hover:text-white transition-colors"
              title="Refresh Dashboard"
            >
              <RefreshCw className={`w-4 h-4 ${refreshing ? "animate-spin" : ""}`} />
            </button>
          </div>
        </div>

        {/* Primary Account & KYC Limits Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Main Wallet Card */}
          <div className="lg:col-span-2 rounded-2xl bg-gradient-to-br from-blue-900/40 via-slate-900 to-slate-900 border border-blue-500/20 p-6 sm:p-8 shadow-2xl relative overflow-hidden">
            <div className="absolute top-0 right-0 -mt-8 -mr-8 w-48 h-48 bg-blue-500/10 rounded-full blur-3xl pointer-events-none" />

            <div className="flex items-center justify-between mb-6">
              <span className="text-xs uppercase font-semibold tracking-wider text-blue-400 bg-blue-500/10 px-3 py-1 rounded-full border border-blue-500/20">
                Primary Wallet ({primaryAccount?.currency || "USD"})
              </span>
              <div className="flex items-center gap-2 text-xs text-slate-400">
                <span>Account:</span>
                <span className="font-mono text-slate-200">{primaryAccount?.accountNumber || "PROVISIONING..."}</span>
                {primaryAccount && (
                  <button
                    onClick={() => copyToClipboard(primaryAccount.accountNumber)}
                    className="hover:text-white"
                  >
                    {copied === primaryAccount.accountNumber ? (
                      <Check className="w-3.5 h-3.5 text-emerald-400" />
                    ) : (
                      <Copy className="w-3.5 h-3.5" />
                    )}
                  </button>
                )}
              </div>
            </div>

            <div className="mb-8">
              <div className="text-xs text-slate-400 mb-1">Available Ledger Balance</div>
              <div className="text-4xl sm:text-5xl font-black text-white tracking-tight">
                ${(primaryAccount?.balance ?? 0).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </div>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-6 border-t border-slate-800/80">
              <Link
                href="/transfers"
                className="flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm transition-colors shadow-lg shadow-blue-500/20"
              >
                <ArrowUpRight className="w-4 h-4" /> Send Funds
              </Link>

              <Link
                href="/payouts"
                className="flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-sm transition-colors border border-slate-700"
              >
                <ArrowDownLeft className="w-4 h-4" /> Withdraw
              </Link>
            </div>
          </div>

          {/* KYC Tier & Daily Limit Progress Card */}
          <div className="rounded-2xl bg-slate-900 border border-slate-800 p-6 flex flex-col justify-between shadow-xl">
            <div>
              <div className="flex items-center justify-between mb-4">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">KYC Verification</span>
                <span className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium">
                  <ShieldCheck className="w-3 h-3" /> Tier {user.kycTier}
                </span>
              </div>

              <h3 className="text-lg font-bold text-white mb-1">{user.kycTierDescription}</h3>
              <p className="text-xs text-slate-400 mb-6">
                Single transaction limit: <span className="text-slate-200 font-semibold">${user.singleTransactionLimit.toLocaleString()}</span>
              </p>

              {/* Progress Bar */}
              <div className="space-y-2">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">Daily limit used</span>
                  <span className="text-slate-200 font-medium font-mono">
                    ${user.dailyUsedToday.toLocaleString()} / ${user.dailyLimit.toLocaleString()}
                  </span>
                </div>

                <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${
                      usedPercentage > 85 ? "bg-rose-500" : usedPercentage > 50 ? "bg-amber-500" : "bg-blue-500"
                    }`}
                    style={{ width: `${usedPercentage}%` }}
                  />
                </div>

                <div className="text-right text-[11px] text-slate-500">
                  ${user.dailyRemainingToday.toLocaleString()} remaining today
                </div>
              </div>
            </div>

            <div className="pt-6 border-t border-slate-800/80 mt-6">
              <Link
                href="/kyc"
                className="w-full inline-flex items-center justify-center py-2 px-3 rounded-lg border border-slate-700 hover:border-slate-600 bg-slate-800/50 hover:bg-slate-800 text-xs text-slate-200 font-medium transition-colors"
              >
                Upgrade Tier Limit →
              </Link>
            </div>
          </div>
        </div>

        {/* All Customer Accounts Table */}
        <div className="rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-xl">
          <h2 className="text-lg font-bold text-white mb-4">Your Financial Accounts</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="text-xs uppercase bg-slate-950 text-slate-400 border-b border-slate-800">
                <tr>
                  <th className="py-3 px-4">Account Name</th>
                  <th className="py-3 px-4">Account Number</th>
                  <th className="py-3 px-4">Type</th>
                  <th className="py-3 px-4">Currency</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Balance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {accounts.map((acc) => (
                  <tr key={acc.accountId} className="hover:bg-slate-800/30 transition-colors">
                    <td className="py-3.5 px-4 font-medium text-white">{acc.accountName}</td>
                    <td className="py-3.5 px-4 font-mono text-xs text-slate-300">{acc.accountNumber}</td>
                    <td className="py-3.5 px-4 text-xs text-slate-400">{acc.accountType}</td>
                    <td className="py-3.5 px-4 text-xs font-semibold">{acc.currency}</td>
                    <td className="py-3.5 px-4">
                      <span className="text-[11px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium">
                        {acc.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right font-mono font-semibold text-white">
                      ${acc.balance.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </main>
    </div>
  );
}

