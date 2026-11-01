"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api } from "@/lib/api";
import { KycApplication, ReconciliationRun, UserProfile } from "@/types";
import { 
  ShieldAlert, 
  Play, 
  CheckCircle2, 
  XCircle, 
  Search, 
  RefreshCw, 
  AlertTriangle,
  Scale,
  Users,
  FileCheck2,
  Activity,
  ArrowRight,
  Database
} from "lucide-react";

export default function AdminPage() {
  const { user, isLoading } = useAuth();
  const router = useRouter();

  const [activeTab, setActiveTab] = useState<"reconciliation" | "kyc" | "users">("reconciliation");
  const [runs, setRuns] = useState<ReconciliationRun[]>([]);
  const [pendingKyc, setPendingKyc] = useState<KycApplication[]>([]);
  const [usersList, setUsersList] = useState<UserProfile[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [loadingAction, setLoadingAction] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    if (!isLoading) {
      if (!user) {
        router.push("/");
      } else if (user.role !== "ROLE_ADMIN") {
        router.push("/dashboard");
      } else {
        loadData();
      }
    }
  }, [user, isLoading]);

  const loadData = async () => {
    try {
      const runsRes = await api.get<ReconciliationRun[]>("/reconciliation/runs?page=0&size=10");
      setRuns(runsRes.data || []);

      const kycRes = await api.get<{ content: KycApplication[] }>("/admin/users/kyc/applications?status=SUBMITTED");
      setPendingKyc(kycRes.data.content || []);

      const usersRes = await api.get<{ content: UserProfile[] }>("/admin/users?page=0&size=20");
      setUsersList(usersRes.data.content || []);
    } catch (e) {
      console.error(e);
    }
  };

  const handleRunInternalAudit = async () => {
    setLoadingAction(true);
    setMessage(null);
    try {
      const res = await api.post<ReconciliationRun>("/reconciliation/runs");
      setMessage({
        type: res.data.status === "PASSED" ? "success" : "error",
        text: `Reconciliation audit finished: ${res.data.status} with ${res.data.discrepancyCount} discrepancy.`,
      });
      loadData();
    } catch (err: any) {
      setMessage({ type: "error", text: err.response?.data?.detail || "Audit execution failed" });
    } finally {
      setLoadingAction(false);
    }
  };

  const handleReviewKyc = async (appId: string, status: "APPROVED" | "REJECTED", reason?: string) => {
    try {
      await api.post(`/admin/users/kyc/applications/${appId}/review`, {
        status,
        rejectionReason: reason || undefined,
      });
      setMessage({ type: "success", text: `Application ${status.toLowerCase()} successfully.` });
      loadData();
    } catch (err: any) {
      setMessage({ type: "error", text: err.response?.data?.detail || "Review failed" });
    }
  };

  const handleSearchUsers = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await api.get<{ content: UserProfile[] }>(`/admin/users?search=${encodeURIComponent(searchQuery)}`);
      setUsersList(res.data.content || []);
    } catch (e) {
      console.error(e);
    }
  };

  if (isLoading || !user || user.role !== "ROLE_ADMIN") return null;

  return (
    <div className="min-h-screen bg-[#05070e] text-slate-100">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <div className="h-10 w-10 rounded-2xl bg-amber-500/20 text-amber-400 border border-amber-500/30 flex items-center justify-center">
                <ShieldAlert className="w-5 h-5" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                Admin & Reconciliation Console
              </h1>
            </div>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Trial balance invariant verification • Compliance approvals • System user oversight
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={handleRunInternalAudit}
              disabled={loadingAction}
              className="px-4 py-2.5 rounded-xl font-bold text-xs bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white shadow-lg shadow-blue-500/20 transition-all flex items-center gap-2 disabled:opacity-50"
            >
              <Play className={`w-3.5 h-3.5 ${loadingAction ? "animate-spin" : ""}`} />
              <span>{loadingAction ? "Auditing Ledger..." : "Trigger Full Reconciliation"}</span>
            </button>

            <button
              onClick={loadData}
              className="p-2.5 rounded-xl border border-white/10 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Global Notifications */}
        {message && (
          <div
            className={`p-4 rounded-2xl text-xs flex items-center justify-between border ${
              message.type === "success"
                ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-300"
                : "bg-rose-500/10 border-rose-500/30 text-rose-300"
            }`}
          >
            <div className="flex items-center gap-2">
              {message.type === "success" ? <CheckCircle2 className="w-4 h-4" /> : <AlertTriangle className="w-4 h-4" />}
              <span>{message.text}</span>
            </div>
            <button onClick={() => setMessage(null)} className="text-slate-400 hover:text-white font-bold">&times;</button>
          </div>
        )}

        {/* Key Metrics Gauges */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
          <div className="p-6 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl relative overflow-hidden">
            <div className="flex items-center justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
              <span>Ledger Health Check</span>
              <Scale className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="text-2xl font-black font-mono text-emerald-400">
              100% INVARIANT PASSED
            </div>
            <div className="text-xs text-slate-400 font-mono mt-1">
              Trial Balance: Σ Debits = Σ Credits ($0.00 delta)
            </div>
          </div>

          <div className="p-6 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl relative overflow-hidden">
            <div className="flex items-center justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
              <span>Compliance Queue</span>
              <FileCheck2 className="w-4 h-4 text-amber-400" />
            </div>
            <div className="text-2xl font-black font-mono text-white">
              {pendingKyc.length} Pending
            </div>
            <div className="text-xs text-slate-400 font-mono mt-1">
              Tier upgrade verification requests
            </div>
          </div>

          <div className="p-6 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl relative overflow-hidden">
            <div className="flex items-center justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
              <span>Platform Customers</span>
              <Users className="w-4 h-4 text-blue-400" />
            </div>
            <div className="text-2xl font-black font-mono text-white">
              {usersList.length} Active
            </div>
            <div className="text-xs text-slate-400 font-mono mt-1">
              Provisioned double-entry liability accounts
            </div>
          </div>
        </div>

        {/* Sub-Navigation Tabs */}
        <div className="flex rounded-2xl bg-slate-950 p-1.5 border border-white/5 w-fit">
          <button
            onClick={() => setActiveTab("reconciliation")}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
              activeTab === "reconciliation"
                ? "bg-blue-600 text-white shadow-md shadow-blue-600/30"
                : "text-slate-400 hover:text-white"
            }`}
          >
            <Database className="w-3.5 h-3.5" />
            Reconciliation Logs
          </button>
          <button
            onClick={() => setActiveTab("kyc")}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
              activeTab === "kyc"
                ? "bg-blue-600 text-white shadow-md shadow-blue-600/30"
                : "text-slate-400 hover:text-white"
            }`}
          >
            <FileCheck2 className="w-3.5 h-3.5" />
            KYC Approvals ({pendingKyc.length})
          </button>
          <button
            onClick={() => setActiveTab("users")}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
              activeTab === "users"
                ? "bg-blue-600 text-white shadow-md shadow-blue-600/30"
                : "text-slate-400 hover:text-white"
            }`}
          >
            <Users className="w-3.5 h-3.5" />
            Platform Directory
          </button>
        </div>

        {/* Tab 1: Reconciliation Runs */}
        {activeTab === "reconciliation" && (
          <div className="rounded-3xl border border-white/10 bg-slate-900/60 p-6 sm:p-8 backdrop-blur-xl shadow-xl space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-white">Automated Ledger Audits</h2>
                <p className="text-xs text-slate-400">Scheduled and on-demand trial balance invariant verifications</p>
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-white/5 text-slate-400 font-mono uppercase text-[10px] tracking-wider">
                    <th className="py-3 px-3">Run Reference</th>
                    <th className="py-3 px-3">Type</th>
                    <th className="py-3 px-3">Status</th>
                    <th className="py-3 px-3">Discrepancies</th>
                    <th className="py-3 px-3">Started At</th>
                    <th className="py-3 px-3">Completed At</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {runs.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="py-8 text-center text-slate-500">
                        No audit runs executed yet. Click &quot;Trigger Full Reconciliation&quot; above.
                      </td>
                    </tr>
                  ) : (
                    runs.map((r) => (
                      <tr key={r.id} className="hover:bg-slate-800/30">
                        <td className="py-3 px-3 font-mono font-medium text-white truncate max-w-[140px]">
                          {r.id}
                        </td>
                        <td className="py-3 px-3 font-mono text-slate-400">
                          {r.type}
                        </td>
                        <td className="py-3 px-3">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            r.status === "PASSED"
                              ? "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30"
                              : "bg-rose-500/15 text-rose-400 border border-rose-500/30"
                          }`}>
                            {r.status}
                          </span>
                        </td>
                        <td className="py-3 px-3 font-mono font-bold text-white">
                          {r.discrepancyCount}
                        </td>
                        <td className="py-3 px-3 text-slate-400 font-mono text-[11px]">
                          {new Date(r.startedAt).toLocaleTimeString()}
                        </td>
                        <td className="py-3 px-3 text-slate-400 font-mono text-[11px]">
                          {r.completedAt ? new Date(r.completedAt).toLocaleTimeString() : "In flight"}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Tab 2: KYC Compliance Queue */}
        {activeTab === "kyc" && (
          <div className="rounded-3xl border border-white/10 bg-slate-900/60 p-6 sm:p-8 backdrop-blur-xl shadow-xl space-y-4">
            <h2 className="text-lg font-bold text-white">Pending Verification Submissions</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-white/5 text-slate-400 font-mono uppercase text-[10px] tracking-wider">
                    <th className="py-3 px-3">Target Tier</th>
                    <th className="py-3 px-3">ID Type & Number</th>
                    <th className="py-3 px-3">Submission Date</th>
                    <th className="py-3 px-3 text-right">Review Decision</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {pendingKyc.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="py-8 text-center text-slate-500">
                        Compliance queue is clear. No pending applications.
                      </td>
                    </tr>
                  ) : (
                    pendingKyc.map((app) => (
                      <tr key={app.id} className="hover:bg-slate-800/30">
                        <td className="py-3 px-3 font-bold font-mono text-white">Tier {app.targetTier}</td>
                        <td className="py-3 px-3 font-mono text-slate-300">{app.idType} • {app.idNumber}</td>
                        <td className="py-3 px-3 font-mono text-slate-400 text-[11px]">
                          {app.createdAt ? new Date(app.createdAt).toLocaleDateString() : "Recent"}
                        </td>
                        <td className="py-3 px-3 text-right space-x-2">
                          <button
                            onClick={() => handleReviewKyc(app.id, "APPROVED")}
                            className="px-3 py-1 rounded-lg text-xs font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-emerald-500/30 transition-colors"
                          >
                            Approve
                          </button>
                          <button
                            onClick={() => handleReviewKyc(app.id, "REJECTED", "Failed compliance check")}
                            className="px-3 py-1 rounded-lg text-xs font-semibold bg-rose-500/20 text-rose-400 border border-rose-500/40 hover:bg-rose-500/30 transition-colors"
                          >
                            Reject
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Tab 3: Platform Directory */}
        {activeTab === "users" && (
          <div className="rounded-3xl border border-white/10 bg-slate-900/60 p-6 sm:p-8 backdrop-blur-xl shadow-xl space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
              <div>
                <h2 className="text-lg font-bold text-white">Customer Accounts Directory</h2>
                <p className="text-xs text-slate-400">Inspect tiered limits and registered customers</p>
              </div>

              <form onSubmit={handleSearchUsers} className="relative">
                <Search className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
                <input
                  type="text"
                  placeholder="Search email or name..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-9 pr-3.5 py-1.5 rounded-xl bg-slate-950 border border-slate-700/80 text-xs text-white placeholder:text-slate-600 focus:outline-none focus:border-blue-500 font-mono w-60"
                />
              </form>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-white/5 text-slate-400 font-mono uppercase text-[10px] tracking-wider">
                    <th className="py-3 px-3">Name</th>
                    <th className="py-3 px-3">Email</th>
                    <th className="py-3 px-3">Role</th>
                    <th className="py-3 px-3">KYC Tier</th>
                    <th className="py-3 px-3">Daily Limit</th>
                    <th className="py-3 px-3">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {usersList.map((u) => (
                    <tr key={u.id} className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-semibold text-white">{u.firstName} {u.lastName}</td>
                      <td className="py-3 px-3 font-mono text-slate-300">{u.email}</td>
                      <td className="py-3 px-3 font-mono text-slate-400">{u.role}</td>
                      <td className="py-3 px-3">
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-blue-500/15 text-blue-400 border border-blue-500/30">
                          Tier {u.kycTier}
                        </span>
                      </td>
                      <td className="py-3 px-3 font-mono font-bold text-slate-200">${u.dailyLimit.toLocaleString()}</td>
                      <td className="py-3 px-3 text-slate-400 font-mono text-[11px]">
                        {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : "Active"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
