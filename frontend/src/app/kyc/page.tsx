"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api } from "@/lib/api";
import { KycApplication } from "@/types";
import { 
  ShieldCheck, 
  Upload, 
  CheckCircle2, 
  XCircle, 
  Clock, 
  AlertCircle, 
  FileText, 
  ArrowRight,
  Sparkles,
  Lock,
  RefreshCw
} from "lucide-react";

export default function KycPage() {
  const { user, isLoading, refreshProfile } = useAuth();
  const router = useRouter();

  const [applications, setApplications] = useState<KycApplication[]>([]);
  const [targetTier, setTargetTier] = useState<number>(2);
  const [idType, setIdType] = useState<string>("PASSPORT");
  const [idNumber, setIdNumber] = useState("");
  const [documentUrl, setDocumentUrl] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    } else if (user) {
      setTargetTier(user.kycTier < 3 ? user.kycTier + 1 : 3);
      loadApplications();
    }
  }, [user, isLoading]);

  const loadApplications = async () => {
    try {
      const res = await api.get<KycApplication[]>("/users/kyc/applications");
      setApplications(res.data || []);
    } catch (e) {
      console.error(e);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setSubmitting(true);

    try {
      await api.post("/users/kyc/upgrade", {
        targetTier,
        idType,
        idNumber,
        documentUrl: documentUrl || "https://secure-vault.fintech.internal/docs/verified-identity.pdf",
      });

      setSuccess("Your KYC verification application has been submitted for automated compliance review!");
      setIdNumber("");
      setDocumentUrl("");
      await refreshProfile();
      loadApplications();
    } catch (err: any) {
      const detail = err.response?.data?.detail || err.response?.data?.message || "Failed to submit verification request";
      setError(detail);
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading || !user) return null;

  const tiers = [
    {
      tier: 1,
      name: "Basic Customer",
      dailyLimit: "$1,000",
      singleLimit: "$500",
      features: ["Email & Phone Verification", "Standard P2P Transfers", "Basic Wallet"],
      badge: "ACTIVE",
    },
    {
      tier: 2,
      name: "Verified Individual",
      dailyLimit: "$10,000",
      singleLimit: "$5,000",
      features: ["Government ID / Passport Check", "Instant Bank Payouts", "Multi-Currency Wallets"],
      badge: user.kycTier >= 2 ? "ACTIVE" : "POPULAR",
    },
    {
      tier: 3,
      name: "Institutional / Enterprise",
      dailyLimit: "$100,000+",
      singleLimit: "Unlimited",
      features: ["Proof of Address & Source of Funds", "Custom Settlement Rails", "Dedicated Priority Support"],
      badge: user.kycTier >= 3 ? "ACTIVE" : "MAX LIMIT",
    },
  ];

  return (
    <div className="min-h-screen bg-[#05070e] text-slate-100">
      <Navbar />

      <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <div className="h-10 w-10 rounded-2xl bg-indigo-600/20 text-indigo-400 border border-indigo-500/30 flex items-center justify-center">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                KYC Verification & Tier Limits
              </h1>
            </div>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Automated AML/CFT compliance verification to scale your transaction allowances
            </p>
          </div>

          <div className="flex items-center gap-2 px-3.5 py-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/10 text-emerald-400 text-xs font-mono">
            <span>Current Status: Tier {user.kycTier} Verified</span>
          </div>
        </div>

        {/* 3-Tier Milestone Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {tiers.map((t) => {
            const isCurrent = user.kycTier === t.tier;
            const isUnlocked = user.kycTier >= t.tier;

            return (
              <div
                key={t.tier}
                className={`relative rounded-3xl p-6 sm:p-7 border backdrop-blur-xl transition-all flex flex-col justify-between ${
                  isCurrent
                    ? "bg-gradient-to-b from-blue-900/30 via-slate-900/80 to-slate-950 border-blue-500/50 shadow-xl shadow-blue-950/30"
                    : "bg-slate-900/60 border-white/10 hover:border-white/20"
                }`}
              >
                {isCurrent && (
                  <div className="absolute -top-3 left-6 px-3 py-0.5 rounded-full bg-blue-600 text-white text-[10px] font-bold tracking-wider uppercase shadow-md shadow-blue-600/40">
                    Your Current Tier
                  </div>
                )}

                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-mono uppercase tracking-widest text-slate-400">
                      Tier {t.tier}
                    </span>
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full font-mono ${
                      isUnlocked ? "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30" : "bg-slate-800 text-slate-400"
                    }`}>
                      {isUnlocked ? "UNLOCKED" : t.badge}
                    </span>
                  </div>

                  <h3 className="text-xl font-bold text-white mb-2">{t.name}</h3>

                  <div className="p-4 rounded-2xl bg-slate-950/80 border border-white/5 my-4">
                    <div className="text-[10px] uppercase font-mono text-slate-400">Daily Allowance</div>
                    <div className="text-2xl font-black font-mono text-emerald-400">{t.dailyLimit}</div>
                    <div className="text-[11px] text-slate-400 font-mono mt-0.5">Single Tx: {t.singleLimit}</div>
                  </div>

                  <ul className="space-y-2.5 text-xs text-slate-300">
                    {t.features.map((f) => (
                      <li key={f} className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>{f}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="mt-6 pt-4 border-t border-white/5">
                  {isCurrent ? (
                    <div className="text-center text-xs text-blue-400 font-semibold font-mono">
                      Active Operating Level
                    </div>
                  ) : isUnlocked ? (
                    <div className="text-center text-xs text-emerald-400 font-semibold font-mono">
                      Already Achieved
                    </div>
                  ) : (
                    <button
                      onClick={() => setTargetTier(t.tier)}
                      className="w-full py-2.5 rounded-xl text-xs font-bold bg-slate-800 hover:bg-slate-700 text-white transition-all flex items-center justify-center gap-1.5"
                    >
                      Apply for Tier {t.tier} <ArrowRight className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Upgrade Application Form */}
        <div className="rounded-3xl border border-white/10 bg-slate-900/70 p-6 sm:p-8 backdrop-blur-2xl shadow-xl space-y-6">
          <div className="border-b border-white/5 pb-4">
            <h2 className="text-lg font-bold text-white">Upgrade Request Form</h2>
            <p className="text-xs text-slate-400">Upload identity credentials to request promotion to Tier {targetTier}</p>
          </div>

          {error && (
            <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs">
              {error}
            </div>
          )}

          {success && (
            <div className="p-4 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              <span>{success}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                  Target Tier
                </label>
                <select
                  value={targetTier}
                  onChange={(e) => setTargetTier(parseInt(e.target.value))}
                  className="w-full px-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white font-mono text-sm focus:outline-none focus:border-blue-500"
                >
                  <option value={2}>Tier 2 ($10,000/day)</option>
                  <option value={3}>Tier 3 ($100,000+/day)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                  Identification Type
                </label>
                <select
                  value={idType}
                  onChange={(e) => setIdType(e.target.value)}
                  className="w-full px-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white text-sm focus:outline-none focus:border-blue-500"
                >
                  <option value="PASSPORT">International Passport</option>
                  <option value="DRIVERS_LICENSE">Driver's License</option>
                  <option value="NATIONAL_ID">National ID Card</option>
                  <option value="TAX_ID">Tax / Corporate ID</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                  Document ID Number
                </label>
                <input
                  type="text"
                  required
                  value={idNumber}
                  onChange={(e) => setIdNumber(e.target.value)}
                  placeholder="e.g. A92817492"
                  className="w-full px-4 py-3 bg-slate-950 border border-slate-700/80 rounded-2xl text-white font-mono text-sm focus:outline-none focus:border-blue-500 placeholder:text-slate-600"
                />
              </div>
            </div>

            {/* Document Upload Area */}
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                Document Upload & Proof of Verification
              </label>
              <div className="border-2 border-dashed border-slate-700 hover:border-blue-500/50 rounded-3xl p-8 bg-slate-950/60 text-center transition-colors">
                <Upload className="w-8 h-8 text-slate-500 mx-auto mb-3" />
                <div className="text-sm font-semibold text-slate-200">
                  Drag and drop proof document or click to browse
                </div>
                <div className="text-xs text-slate-500 mt-1">
                  Supports PDF, PNG, JPG up to 10MB (Encrypted in transit)
                </div>
                <input
                  type="text"
                  value={documentUrl}
                  onChange={(e) => setDocumentUrl(e.target.value)}
                  placeholder="Or provide secure document storage URL"
                  className="mt-4 max-w-md mx-auto w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs text-white placeholder:text-slate-600 focus:outline-none focus:border-blue-500 text-center font-mono"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={submitting || !idNumber}
              className="w-full py-4 rounded-2xl font-bold text-sm text-white bg-gradient-to-r from-indigo-600 via-blue-600 to-indigo-600 hover:from-indigo-500 hover:to-blue-500 shadow-xl shadow-indigo-500/25 transition-all flex items-center justify-center gap-2 disabled:opacity-40"
            >
              {submitting ? (
                <RefreshCw className="w-4 h-4 animate-spin" />
              ) : (
                <>
                  <span>Submit KYC Tier {targetTier} Application</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>
        </div>

        {/* Previous Applications History */}
        <div className="rounded-3xl border border-white/10 bg-slate-900/60 p-6 sm:p-8 backdrop-blur-xl shadow-xl space-y-4">
          <h2 className="text-lg font-bold text-white">Your Compliance Review History</h2>
          
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-white/5 text-slate-400 font-mono uppercase text-[10px] tracking-wider">
                  <th className="py-3 px-3">Target Tier</th>
                  <th className="py-3 px-3">ID Type & Number</th>
                  <th className="py-3 px-3">Status</th>
                  <th className="py-3 px-3">Submitted Date</th>
                  <th className="py-3 px-3">Review Notes</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {applications.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-slate-500">
                      No prior KYC applications on record.
                    </td>
                  </tr>
                ) : (
                  applications.map((app) => (
                    <tr key={app.id} className="hover:bg-slate-800/30">
                      <td className="py-3 px-3 font-bold text-white font-mono">
                        Tier {app.targetTier}
                      </td>
                      <td className="py-3 px-3 text-slate-300 font-mono">
                        {app.idType} • {app.idNumber}
                      </td>
                      <td className="py-3 px-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold tracking-wide ${
                          app.status === "APPROVED"
                            ? "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30"
                            : app.status === "SUBMITTED"
                            ? "bg-amber-500/15 text-amber-400 border border-amber-500/30"
                            : "bg-rose-500/15 text-rose-400 border border-rose-500/30"
                        }`}>
                          {app.status}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-slate-400 font-mono text-[11px]">
                        {app.createdAt ? new Date(app.createdAt).toLocaleDateString() : "Recent"}
                      </td>
                      <td className="py-3 px-3 text-slate-400 text-xs">
                        {app.rejectionReason || "Awaiting compliance verification"}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </main>
    </div>
  );
}
