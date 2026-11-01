"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { 
  ArrowRight, 
  Lock, 
  Mail, 
  User, 
  ShieldCheck, 
  Sparkles, 
  CheckCircle2, 
  Zap, 
  Database, 
  RefreshCw,
  Coins
} from "lucide-react";

export default function AuthPage() {
  const [isLogin, setIsLogin] = useState(true);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const { login, register, user } = useAuth();
  const router = useRouter();

  if (user) {
    router.push("/dashboard");
    return null;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      if (isLogin) {
        await login(email, password);
      } else {
        await register(firstName, lastName, email, password);
      }
      router.push("/dashboard");
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.response?.data?.message || "Authentication failed. Check your credentials.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const fillDemoCredentials = (role: "user" | "admin") => {
    setIsLogin(true);
    setError(null);
    if (role === "admin") {
      setEmail("admin@fintech.com");
      setPassword("Admin123!");
    } else {
      setEmail("john.doe@fintech.com");
      setPassword("Password123!");
    }
  };

  return (
    <div className="min-h-screen bg-[#05070e] relative overflow-hidden flex flex-col justify-center">
      {/* Background Radial Glow Accents */}
      <div className="absolute top-0 left-1/4 w-[600px] h-[600px] bg-blue-600/10 rounded-full blur-3xl pointer-events-none -translate-y-1/2"></div>
      <div className="absolute bottom-0 right-1/4 w-[600px] h-[600px] bg-indigo-600/10 rounded-full blur-3xl pointer-events-none translate-y-1/2"></div>
      <div className="absolute top-1/2 right-10 w-[300px] h-[300px] bg-emerald-500/5 rounded-full blur-2xl pointer-events-none"></div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 relative z-10 w-full">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          
          {/* Left Column: Product Showcase */}
          <div className="lg:col-span-7 space-y-8 text-left">
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full border border-blue-500/30 bg-blue-500/10 text-blue-400 text-xs font-semibold uppercase tracking-wider shadow-sm shadow-blue-500/10">
              <Sparkles className="w-3.5 h-3.5" /> Enterprise Double-Entry Ledger Core
            </div>

            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-black text-white tracking-tight leading-[1.1]">
              Financial Ledger & <br />
              <span className="bg-gradient-to-r from-blue-400 via-indigo-300 to-cyan-400 bg-clip-text text-transparent">
                Payment Engine
              </span>
            </h1>

            <p className="text-base sm:text-lg text-slate-400 max-w-xl leading-relaxed">
              Designed for high-concurrency fintech workloads. Features strict mathematical invariant preservation, 
              distributed locking, idempotency guarantees, and cryptographically verified webhook settlement.
            </p>

            {/* Core Architectural Pillars */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-xl">
              <div className="p-4 rounded-2xl border border-white/5 bg-slate-900/60 backdrop-blur-md">
                <div className="flex items-center gap-2.5 text-blue-400 font-semibold text-sm mb-1">
                  <Database className="w-4 h-4" /> Double-Entry Accounting
                </div>
                <p className="text-xs text-slate-400 font-mono">
                  Σ Debits = Σ Credits. Zero in-place balance updates. Immutable historical ledger.
                </p>
              </div>

              <div className="p-4 rounded-2xl border border-white/5 bg-slate-900/60 backdrop-blur-md">
                <div className="flex items-center gap-2.5 text-emerald-400 font-semibold text-sm mb-1">
                  <Zap className="w-4 h-4" /> Strict Idempotency
                </div>
                <p className="text-xs text-slate-400 font-mono">
                  Redis UUIDv4 caching prevents double charges on connection drops or retries.
                </p>
              </div>

              <div className="p-4 rounded-2xl border border-white/5 bg-slate-900/60 backdrop-blur-md">
                <div className="flex items-center gap-2.5 text-indigo-400 font-semibold text-sm mb-1">
                  <Coins className="w-4 h-4" /> Multi-Currency Wallets
                </div>
                <p className="text-xs text-slate-400 font-mono">
                  Deterministic pessimistic locks (`SELECT ... FOR UPDATE`) eliminate race conditions.
                </p>
              </div>

              <div className="p-4 rounded-2xl border border-white/5 bg-slate-900/60 backdrop-blur-md">
                <div className="flex items-center gap-2.5 text-cyan-400 font-semibold text-sm mb-1">
                  <ShieldCheck className="w-4 h-4" /> Tiered KYC Governance
                </div>
                <p className="text-xs text-slate-400 font-mono">
                  Dynamic daily limit enforcement and automated trial balance reconciliation.
                </p>
              </div>
            </div>

            {/* Live Ledger Invariant Ticker */}
            <div className="p-3.5 rounded-2xl border border-white/10 bg-slate-950/70 backdrop-blur-lg flex items-center justify-between max-w-xl">
              <div className="flex items-center gap-2 text-xs">
                <span className="inline-block h-2 w-2 rounded-full bg-emerald-400 animate-pulse"></span>
                <span className="font-mono text-slate-300">Live Engine Health:</span>
                <span className="text-emerald-400 font-mono font-bold">100% INVARIANT PRESERVED</span>
              </div>
              <span className="text-[11px] font-mono text-slate-400">P99 Latency: 14ms</span>
            </div>
          </div>

          {/* Right Column: Modern Auth Box */}
          <div className="lg:col-span-5 w-full max-w-md mx-auto">
            <div className="p-8 sm:p-10 rounded-3xl border border-white/10 bg-slate-900/80 backdrop-blur-2xl shadow-2xl shadow-blue-950/40 relative">
              
              {/* Header */}
              <div className="text-center mb-6">
                <div className="inline-flex h-12 w-12 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-600 items-center justify-center font-bold text-white text-xl shadow-lg shadow-blue-500/25 mb-3">
                  ⚡
                </div>
                <h2 className="text-2xl font-bold text-white tracking-tight">
                  {isLogin ? "Welcome Back" : "Create Account"}
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  {isLogin 
                    ? "Access your wallets, ledger transactions & payouts" 
                    : "Instantly spin up a multi-currency ledger account"}
                </p>
              </div>

              {/* Demo Fill Pills */}
              <div className="mb-6">
                <div className="text-[11px] text-slate-400 font-medium uppercase tracking-wider mb-2 text-center">
                  Quick Demo Login
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => fillDemoCredentials("user")}
                    className="px-3 py-2 rounded-xl text-xs font-semibold bg-slate-800/80 hover:bg-slate-700/80 text-blue-400 border border-slate-700/60 transition-all flex items-center justify-center gap-1.5"
                  >
                    <User className="w-3.5 h-3.5" /> Customer Demo
                  </button>
                  <button
                    type="button"
                    onClick={() => fillDemoCredentials("admin")}
                    className="px-3 py-2 rounded-xl text-xs font-semibold bg-slate-800/80 hover:bg-slate-700/80 text-amber-400 border border-slate-700/60 transition-all flex items-center justify-center gap-1.5"
                  >
                    <ShieldCheck className="w-3.5 h-3.5" /> Admin Demo
                  </button>
                </div>
              </div>

              {/* Tabs */}
              <div className="flex rounded-xl bg-slate-950/80 p-1 mb-6 border border-white/5">
                <button
                  type="button"
                  onClick={() => { setIsLogin(true); setError(null); }}
                  className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all ${
                    isLogin ? "bg-blue-600 text-white shadow-md shadow-blue-600/30" : "text-slate-400 hover:text-white"
                  }`}
                >
                  Sign In
                </button>
                <button
                  type="button"
                  onClick={() => { setIsLogin(false); setError(null); }}
                  className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all ${
                    !isLogin ? "bg-blue-600 text-white shadow-md shadow-blue-600/30" : "text-slate-400 hover:text-white"
                  }`}
                >
                  Create Account
                </button>
              </div>

              {error && (
                <div className="mb-5 p-3.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-start gap-2">
                  <span className="font-bold">Error:</span>
                  <span>{error}</span>
                </div>
              )}

              <form className="space-y-4" onSubmit={handleSubmit}>
                {!isLogin && (
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-slate-300 mb-1">First Name</label>
                      <input
                        type="text"
                        required
                        value={firstName}
                        onChange={(e) => setFirstName(e.target.value)}
                        className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700/80 rounded-xl text-white text-sm focus:outline-none focus:border-blue-500"
                        placeholder="John"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-300 mb-1">Last Name</label>
                      <input
                        type="text"
                        required
                        value={lastName}
                        onChange={(e) => setLastName(e.target.value)}
                        className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700/80 rounded-xl text-white text-sm focus:outline-none focus:border-blue-500"
                        placeholder="Doe"
                      />
                    </div>
                  </div>
                )}

                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1">Email Address</label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
                    <input
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full pl-10 pr-3.5 py-2.5 bg-slate-950 border border-slate-700/80 rounded-xl text-white text-sm focus:outline-none focus:border-blue-500 placeholder:text-slate-600"
                      placeholder="name@company.com"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1">Password</label>
                  <div className="relative">
                    <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
                    <input
                      type="password"
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="w-full pl-10 pr-3.5 py-2.5 bg-slate-950 border border-slate-700/80 rounded-xl text-white text-sm focus:outline-none focus:border-blue-500 placeholder:text-slate-600"
                      placeholder="••••••••••••"
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full mt-2 py-3 px-4 rounded-xl font-semibold text-sm text-white bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 shadow-lg shadow-blue-500/25 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
                >
                  {loading ? (
                    <RefreshCw className="w-4 h-4 animate-spin" />
                  ) : (
                    <>
                      {isLogin ? "Sign In to Platform" : "Complete Registration"}
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>
              </form>

              {/* Security Seal Footer */}
              <div className="mt-6 pt-5 border-t border-white/5 flex items-center justify-center gap-4 text-[11px] text-slate-500">
                <span className="flex items-center gap-1">
                  <CheckCircle2 className="w-3.5 h-3.5 text-blue-400" /> 256-Bit TLS 1.3
                </span>
                <span>•</span>
                <span className="flex items-center gap-1">
                  <CheckCircle2 className="w-3.5 h-3.5 text-blue-400" /> SOC-2 Ready
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
