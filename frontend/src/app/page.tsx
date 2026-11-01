"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { ArrowRight, Lock, Mail, User, ShieldCheck, Sparkles } from "lucide-react";

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
      const msg = err.response?.data?.detail || err.response?.data?.message || "Authentication failed";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col justify-center py-12 sm:px-6 lg:px-8 bg-slate-950">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="inline-flex h-12 w-12 rounded-xl bg-blue-600 items-center justify-center font-bold text-white text-xl shadow-xl shadow-blue-500/20 mb-4">
          ⚡
        </div>
        <h2 className="text-3xl font-extrabold tracking-tight text-white">
          Fintech Core Platform
        </h2>
        <p className="mt-2 text-sm text-slate-400">
          Double-Entry Ledger • Strict Idempotency • Concurrency Controls
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-slate-900 py-8 px-4 shadow-2xl border border-slate-800 sm:rounded-2xl sm:px-10">
          <div className="flex rounded-lg bg-slate-800/60 p-1 mb-6 border border-slate-700/50">
            <button
              onClick={() => { setIsLogin(true); setError(null); }}
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-all ${
                isLogin ? "bg-blue-600 text-white shadow" : "text-slate-400 hover:text-white"
              }`}
            >
              Sign In
            </button>
            <button
              onClick={() => { setIsLogin(false); setError(null); }}
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-all ${
                !isLogin ? "bg-blue-600 text-white shadow" : "text-slate-400 hover:text-white"
              }`}
            >
              Create Account
            </button>
          </div>

          {error && (
            <div className="mb-4 p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-400 text-sm">
              {error}
            </div>
          )}

          <form className="space-y-4" onSubmit={handleSubmit}>
            {!isLogin && (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-300">First Name</label>
                  <div className="mt-1 relative">
                    <input
                      type="text"
                      required
                      value={firstName}
                      onChange={(e) => setFirstName(e.target.value)}
                      className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                      placeholder="John"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-300">Last Name</label>
                  <div className="mt-1 relative">
                    <input
                      type="text"
                      required
                      value={lastName}
                      onChange={(e) => setLastName(e.target.value)}
                      className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                      placeholder="Doe"
                    />
                  </div>
                </div>
              </div>
            )}

            <div>
              <label className="block text-xs font-medium text-slate-300">Email address</label>
              <div className="mt-1 relative">
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3 py-2 pl-9 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                  placeholder="name@example.com"
                />
                <Mail className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-slate-300">Password</label>
              <div className="mt-1 relative">
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full px-3 py-2 pl-9 bg-slate-950 border border-slate-700 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500"
                  placeholder="••••••••"
                />
                <Lock className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full mt-2 flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm transition-colors shadow-lg shadow-blue-500/20 disabled:opacity-50"
            >
              {loading ? "Processing..." : isLogin ? "Sign In" : "Register Wallet"}
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          <div className="mt-6 pt-6 border-t border-slate-800 text-center text-xs text-slate-500 space-y-1">
            <div className="flex items-center justify-center gap-1.5 text-slate-400">
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
              Secured with Spring Security & JWT 256-bit HMAC
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

