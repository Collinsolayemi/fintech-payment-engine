"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api } from "@/lib/api";
import { KycApplication } from "@/types";
import { ShieldCheck, Upload, CheckCircle2, XCircle, Clock, AlertCircle } from "lucide-react";

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
      setApplications(res.data);
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
        documentUrl: documentUrl || undefined,
      });

      setSuccess("Your KYC verification application was submitted for admin compliance review!");
      setIdNumber("");
      setDocumentUrl("");
      await refreshProfile();
      loadApplications();
    } catch (err: any) {
      const detail = err.response?.data?.detail || err.response?.data?.message || "Failed to submit application";
      setError(detail);
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading || !user) return null;

  return (
    <div className="min-h-screen bg-slate-950">
      <Navbar />

      <main className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
            <ShieldCheck className="w-6 h-6 text-emerald-400" />
            KYC Verification & Tier Limits
          </h1>
          <p className="text-sm text-slate-400">
            Verify your identity to increase transaction thresholds and unlock higher daily volume.
          </p>
        </div>

        {/* Tier Overview Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className={`p-5 rounded-2xl border ${user.kycTier === 1 ? "bg-blue-950/20 border-blue-500/50" : "bg-slate-900 border-slate-800"}`}>
            <div className="flex justify-between items-center mb-2">
              <span className="text-xs font-semibold uppercase text-slate-400">Tier 1</span>
              {user.kycTier === 1 && (
                <span className="text-[10px] bg-blue-500/20 text-blue-400 px-2 py-0.5 rounded-full font-bold">CURRENT</span>
              )}
            </div>
            <div className="text-lg font-bold text-white mb-2">Basic</div>
            <ul className="text-xs text-slate-400 space-y-1.5">
              <li>• Single transfer: <strong className="text-slate-200">$500</strong></li>
              <li>• Daily limit: <strong className="text-slate-200">$1,000</strong></li>
              <li>• Email verification required</li>
            </ul>
          </div>

          <div className={`p-5 rounded-2xl border ${user.kycTier === 2 ? "bg-blue-950/20 border-blue-500/50" : "bg-slate-900 border-slate-800"}`}>
            <div className="flex justify-between items-center mb-2">
              <span className="text-xs font-semibold uppercase text-slate-400">Tier 2</span>
              {user.kycTier === 2 && (
                <span className="text-[10px] bg-blue-500/20 text-blue-400 px-2 py-0.5 rounded-full font-bold">CURRENT</span>
              )}
            </div>
            <div className="text-lg font-bold text-white mb-2">Standard</div>
            <ul className="text-xs text-slate-400 space-y-1.5">
              <li>• Single transfer: <strong className="text-slate-200">$5,000</strong></li>
              <li>• Daily limit: <strong className="text-slate-200">$10,000</strong></li>
              <li>• Government ID / Passport</li>
            </ul>
          </div>

          <div className={`p-5 rounded-2xl border ${user.kycTier === 3 ? "bg-blue-950/20 border-blue-500/50" : "bg-slate-900 border-slate-800"}`}>
            <div className="flex justify-between items-center mb-2">
              <span className="text-xs font-semibold uppercase text-slate-400">Tier 3</span>
              {user.kycTier === 3 && (
                <span className="text-[10px] bg-blue-500/20 text-blue-400 px-2 py-0.5 rounded-full font-bold">CURRENT</span>
              )}
            </div>
            <div className="text-lg font-bold text-white mb-2">Enhanced</div>
            <ul className="text-xs text-slate-400 space-y-1.5">
              <li>• Single transfer: <strong className="text-slate-200">$50,000</strong></li>
              <li>• Daily limit: <strong className="text-slate-200">$100,000</strong></li>
              <li>• Proof of Address & Compliance</li>
            </ul>
          </div>
        </div>

        {error && (
          <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-sm flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            {error}
          </div>
        )}

        {success && (
          <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-sm flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            {success}
          </div>
        )}

        {/* Upgrade Form (if not Tier 3) */}
        {user.kycTier < 3 ? (
          <div className="rounded-2xl bg-slate-900 border border-slate-800 p-6 sm:p-8 shadow-xl">
            <h2 className="text-lg font-bold text-white mb-4">Request Tier {user.kycTier + 1} Upgrade</h2>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Identification Document Type
                  </label>
                  <select
                    value={idType}
                    onChange={(e) => setIdType(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                  >
                    <option value="PASSPORT">International Passport</option>
                    <option value="NATIONAL_ID">National ID Card / NIN</option>
                    <option value="DRIVERS_LICENSE">Driver's License</option>
                    <option value="BVN">Bank Verification Number (BVN)</option>
                    <option value="RESIDENCE_PERMIT">Residence Permit</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Document / ID Number
                  </label>
                  <input
                    type="text"
                    required
                    value={idNumber}
                    onChange={(e) => setIdNumber(e.target.value)}
                    placeholder="e.g. A12345678"
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Document Image URL (Cloud / S3 link)
                </label>
                <input
                  type="url"
                  value={documentUrl}
                  onChange={(e) => setDocumentUrl(e.target.value)}
                  placeholder="https://storage.example.com/kyc-docs/passport.jpg"
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                />
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="py-2.5 px-6 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm transition-colors shadow-lg shadow-blue-500/20 disabled:opacity-50"
              >
                {submitting ? "Submitting..." : `Submit for Tier ${user.kycTier + 1} Review`}
              </button>
            </form>
          </div>
        ) : (
          <div className="p-6 rounded-2xl bg-emerald-950/20 border border-emerald-500/30 text-emerald-300 text-sm flex items-center gap-3">
            <CheckCircle2 className="w-5 h-5 text-emerald-400" />
            <div>
              <span className="font-semibold block text-white">Maximum Tier Reached</span>
              You have already achieved Tier 3 status with maximum allowable limits ($100,000 / day).
            </div>
          </div>
        )}

        {/* History Table */}
        <div className="rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-xl">
          <h2 className="text-base font-bold text-white mb-4">Verification Application History</h2>

          {applications.length === 0 ? (
            <div className="py-8 text-center text-slate-500 text-sm">
              No verification applications submitted yet.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-slate-300">
                <thead className="uppercase bg-slate-950 text-slate-400 border-b border-slate-800">
                  <tr>
                    <th className="py-2.5 px-3">Target Tier</th>
                    <th className="py-2.5 px-3">Document Type</th>
                    <th className="py-2.5 px-3">ID Number</th>
                    <th className="py-2.5 px-3">Status</th>
                    <th className="py-2.5 px-3">Reviewer Notes</th>
                    <th className="py-2.5 px-3">Submitted At</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {applications.map((app) => (
                    <tr key={app.id}>
                      <td className="py-3 px-3 font-bold text-white">Tier {app.targetTier}</td>
                      <td className="py-3 px-3">{app.idType}</td>
                      <td className="py-3 px-3 font-mono">{app.idNumber}</td>
                      <td className="py-3 px-3">
                        <span
                          className={`inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full font-medium ${
                            app.status === "APPROVED"
                              ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                              : app.status === "SUBMITTED"
                              ? "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                              : "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                          }`}
                        >
                          {app.status === "APPROVED" ? (
                            <CheckCircle2 className="w-3 h-3" />
                          ) : app.status === "SUBMITTED" ? (
                            <Clock className="w-3 h-3" />
                          ) : (
                            <XCircle className="w-3 h-3" />
                          )}
                          {app.status}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-slate-400">{app.rejectionReason || "—"}</td>
                      <td className="py-3 px-3 text-slate-500">{new Date(app.createdAt).toLocaleDateString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

