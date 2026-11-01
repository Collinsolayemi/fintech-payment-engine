"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api } from "@/lib/api";
import { KycApplication, ReconciliationRun, UserProfile } from "@/types";
import { ShieldAlert, Play, CheckCircle2, XCircle, Search, RefreshCw, AlertTriangle } from "lucide-react";

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
      setRuns(runsRes.data);

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
        text: `Internal audit completed with status: ${res.data.status} (${res.data.discrepancyCount} discrepancies).`,
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
    <div className="min-h-screen bg-slate-950">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
              <ShieldAlert className="w-6 h-6 text-amber-500" />
              Administrative Back-Office
            </h1>
            <p className="text-sm text-slate-400">
              Audit double-entry ledger invariants, review KYC applications, and manage platform users.
            </p>
          </div>

          <div className="flex rounded-lg bg-slate-900 border border-slate-800 p-1">
            <button
              onClick={() => setActiveTab("reconciliation")}
              className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                activeTab === "reconciliation" ? "bg-blue-600 text-white" : "text-slate-400 hover:text-white"
              }`}
            >
              Ledger Reconciliation
            </button>
            <button
              onClick={() => setActiveTab("kyc")}
              className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors flex items-center gap-1.5 ${
                activeTab === "kyc" ? "bg-blue-600 text-white" : "text-slate-400 hover:text-white"
              }`}
            >
              KYC Queue
              {pendingKyc.length > 0 && (
                <span className="h-4 w-4 rounded-full bg-rose-500 text-white text-[10px] flex items-center justify-center font-bold">
                  {pendingKyc.length}
                </span>
              )}
            </button>
            <button
              onClick={() => setActiveTab("users")}
              className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                activeTab === "users" ? "bg-blue-600 text-white" : "text-slate-400 hover:text-white"
              }`}
            >
              Users Directory
            </button>
          </div>
        </div>

        {message && (
          <div
            className={`p-4 rounded-xl border text-sm flex items-center gap-2 ${
              message.type === "success"
                ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-300"
                : "bg-rose-500/10 border-rose-500/30 text-rose-400"
            }`}
          >
            {message.type === "success" ? <CheckCircle2 className="w-4 h-4" /> : <AlertTriangle className="w-4 h-4" />}
            {message.text}
          </div>
        )}

        {/* Tab 1: Reconciliation */}
        {activeTab === "reconciliation" && (
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-slate-900 border border-slate-800 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 shadow-xl">
              <div>
                <h3 className="text-base font-bold text-white mb-1">On-Demand Integrity Audit</h3>
                <p className="text-xs text-slate-400">
                  Verifies Σ Debits == Σ Credits, per-currency trial balance, zero negative wallets, and escrow alignment.
                </p>
              </div>

              <button
                onClick={handleRunInternalAudit}
                disabled={loadingAction}
                className="inline-flex items-center gap-2 py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-medium text-xs transition-colors shadow-lg shadow-blue-500/20 disabled:opacity-50"
              >
                <Play className="w-4 h-4 fill-current" />
                {loadingAction ? "Auditing..." : "Trigger Full Integrity Audit"}
              </button>
            </div>

            <div className="rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-xl">
              <h3 className="text-base font-bold text-white mb-4">Audit Execution Log</h3>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="uppercase bg-slate-950 text-slate-400 border-b border-slate-800 font-mono">
                    <tr>
                      <th className="py-2.5 px-3">Type</th>
                      <th className="py-2.5 px-3">Status</th>
                      <th className="py-2.5 px-3">Total Debits</th>
                      <th className="py-2.5 px-3">Total Credits</th>
                      <th className="py-2.5 px-3">Discrepancies</th>
                      <th className="py-2.5 px-3">Triggered By</th>
                      <th className="py-2.5 px-3">Started</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono">
                    {runs.map((r) => (
                      <tr key={r.id}>
                        <td className="py-3 px-3 font-medium text-white">{r.type}</td>
                        <td className="py-3 px-3">
                          <span
                            className={`inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full font-medium ${
                              r.status === "PASSED"
                                ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                                : "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                            }`}
                          >
                            {r.status}
                          </span>
                        </td>
                        <td className="py-3 px-3 font-sans text-slate-200">
                          {r.totalDebits !== undefined ? `$${r.totalDebits?.toFixed(2)}` : "—"}
                        </td>
                        <td className="py-3 px-3 font-sans text-slate-200">
                          {r.totalCredits !== undefined ? `$${r.totalCredits?.toFixed(2)}` : "—"}
                        </td>
                        <td className="py-3 px-3 font-bold text-white">{r.discrepancyCount}</td>
                        <td className="py-3 px-3 font-sans text-slate-400">{r.triggeredBy}</td>
                        <td className="py-3 px-3 font-sans text-slate-500">
                          {new Date(r.startedAt).toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: KYC Adjudication Queue */}
        {activeTab === "kyc" && (
          <div className="rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-xl space-y-4">
            <h3 className="text-base font-bold text-white">Pending KYC Tier Upgrade Applications</h3>

            {pendingKyc.length === 0 ? (
              <div className="py-12 text-center text-slate-500 text-sm">
                Queue is clear. No pending KYC applications under review.
              </div>
            ) : (
              <div className="space-y-3">
                {pendingKyc.map((app) => (
                  <div
                    key={app.id}
                    className="p-4 rounded-xl bg-slate-950 border border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-4"
                  >
                    <div className="space-y-1 text-xs">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-white text-sm">{app.userEmail}</span>
                        <span className="px-2 py-0.5 rounded-full bg-blue-500/10 text-blue-400 font-semibold text-[11px]">
                          Target Tier {app.targetTier}
                        </span>
                      </div>
                      <div className="text-slate-400 font-mono">
                        {app.idType}: <strong className="text-slate-200">{app.idNumber}</strong>
                        {app.documentUrl && (
                          <a
                            href={app.documentUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="ml-2 text-blue-400 underline hover:text-blue-300"
                          >
                            [View Document Image]
                          </a>
                        )}
                      </div>
                      <div className="text-[11px] text-slate-500">
                        Submitted: {new Date(app.createdAt).toLocaleString()}
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleReviewKyc(app.id, "APPROVED")}
                        className="py-1.5 px-3 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold transition-colors"
                      >
                        Approve Upgrade
                      </button>
                      <button
                        onClick={() => {
                          const reason = prompt("Enter rejection reason:");
                          if (reason) handleReviewKyc(app.id, "REJECTED", reason);
                        }}
                        className="py-1.5 px-3 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 border border-rose-500/30 text-xs font-medium transition-colors"
                      >
                        Reject
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Users Directory */}
        {activeTab === "users" && (
          <div className="rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-xl space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <h3 className="text-base font-bold text-white">Platform Customer Accounts</h3>

              <form onSubmit={handleSearchUsers} className="relative sm:w-64">
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search by email or name..."
                  className="w-full pl-8 pr-3 py-1.5 bg-slate-950 border border-slate-700 rounded-lg text-xs text-white focus:outline-none focus:border-blue-500"
                />
                <Search className="w-3.5 h-3.5 text-slate-500 absolute left-2.5 top-2" />
              </form>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="uppercase bg-slate-950 text-slate-400 border-b border-slate-800 font-mono">
                  <tr>
                    <th className="py-2.5 px-3">Name</th>
                    <th className="py-2.5 px-3">Email</th>
                    <th className="py-2.5 px-3">Role</th>
                    <th className="py-2.5 px-3">KYC Tier</th>
                    <th className="py-2.5 px-3">Single Limit</th>
                    <th className="py-2.5 px-3">Daily Limit</th>
                    <th className="py-2.5 px-3">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-sans">
                  {usersList.map((u) => (
                    <tr key={u.id} className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-medium text-white">{u.firstName} {u.lastName}</td>
                      <td className="py-3 px-3 font-mono text-slate-300">{u.email}</td>
                      <td className="py-3 px-3 font-mono text-slate-400">{u.role}</td>
                      <td className="py-3 px-3">
                        <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20">
                          Tier {u.kycTier}
                        </span>
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-200">${u.singleTransactionLimit.toLocaleString()}</td>
                      <td className="py-3 px-3 font-mono text-slate-200">${u.dailyLimit.toLocaleString()}</td>
                      <td className="py-3 px-3 text-slate-500">{new Date(u.createdAt).toLocaleDateString()}</td>
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

