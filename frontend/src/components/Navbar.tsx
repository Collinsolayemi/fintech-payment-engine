"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { 
  ShieldCheck, 
  Wallet, 
  ArrowLeftRight, 
  Landmark, 
  FileCheck2, 
  LogOut, 
  ShieldAlert,
  Activity,
  Menu,
  X,
  CreditCard,
  Globe2
} from "lucide-react";
import { useState } from "react";

export function Navbar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  if (!user) return null;

  const isAdmin = user.role === "ROLE_ADMIN";

  const navLinks = [
    { href: "/dashboard", label: "Wallets", icon: Wallet },
    { href: "/transfers", label: "P2P Transfers", icon: ArrowLeftRight },
    { href: "/exchange", label: "FX Swap", icon: Globe2 },
    { href: "/cards", label: "Cards", icon: CreditCard },
    { href: "/payouts", label: "Bank Payouts", icon: Landmark },
    { href: "/kyc", label: "KYC Center", icon: FileCheck2 },
    ...(isAdmin ? [{ href: "/admin", label: "Admin & Ledger", icon: ShieldAlert }] : []),
  ];

  const initials = `${user.firstName?.[0] || ""}${user.lastName?.[0] || ""}`.toUpperCase() || "U";

  return (
    <header className="sticky top-0 z-50 border-b border-white/5 bg-slate-950/75 backdrop-blur-xl transition-all">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Brand & Nav */}
        <div className="flex items-center gap-8">
          <Link href="/dashboard" className="flex items-center gap-3 group">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-tr from-blue-600 via-indigo-600 to-cyan-400 p-[1px] shadow-lg shadow-blue-500/20 group-hover:shadow-blue-500/40 transition-shadow">
              <div className="h-full w-full bg-slate-950 rounded-[11px] flex items-center justify-center font-bold text-white text-base">
                ⚡
              </div>
            </div>
            <div>
              <span className="font-bold text-lg tracking-tight text-white group-hover:text-blue-400 transition-colors">
                Fintech<span className="text-blue-500">Core</span>
              </span>
              <div className="hidden sm:flex items-center gap-1.5 text-[10px] text-emerald-400 font-mono tracking-wider uppercase">
                <span className="inline-block h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
                Ledger ACID
              </div>
            </div>
          </Link>

          {/* Desktop Nav Items */}
          <nav className="hidden lg:flex items-center gap-1">
            {navLinks.map((link) => {
              const Icon = link.icon;
              const isActive = pathname === link.href;
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold tracking-wide transition-all ${
                    isActive
                      ? "bg-blue-600/15 text-blue-400 border border-blue-500/30 shadow-sm shadow-blue-500/10"
                      : "text-slate-400 hover:text-slate-100 hover:bg-slate-900/60 border border-transparent"
                  }`}
                >
                  <Icon className={`w-4 h-4 ${isActive ? "text-blue-400" : "text-slate-400"}`} />
                  {link.label}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* User Badge & Actions */}
        <div className="flex items-center gap-3">
          {/* KYC Tier Tag */}
          <Link 
            href="/kyc"
            className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/10 hover:bg-emerald-500/15 text-xs text-emerald-400 transition-colors"
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            <span className="font-semibold">Tier {user.kycTier}</span>
            <span className="text-emerald-500/40">•</span>
            <span className="font-mono text-[11px] text-emerald-300">${user.dailyLimit.toLocaleString()} Limit</span>
          </Link>

          {/* User Profile Pill */}
          <div className="flex items-center gap-3 pl-2 border-l border-white/5">
            <div className="hidden sm:block text-right">
              <div className="flex items-center justify-end gap-1.5">
                <span className="text-sm font-semibold text-slate-100">
                  {user.firstName} {user.lastName}
                </span>
                {isAdmin && (
                  <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-400 border border-amber-500/30">
                    ADMIN
                  </span>
                )}
              </div>
              <div className="text-xs text-slate-400 font-mono truncate max-w-[140px]">{user.email}</div>
            </div>

            <div className="h-9 w-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-700 flex items-center justify-center font-bold text-white text-xs shadow-md shadow-blue-600/20 border border-white/10">
              {initials}
            </div>

            <button
              onClick={logout}
              title="Logout"
              className="p-2 rounded-xl text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 border border-transparent hover:border-rose-500/20 transition-all"
            >
              <LogOut className="w-4 h-4" />
            </button>

            {/* Mobile Menu Toggle */}
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="lg:hidden p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            >
              {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Drawer */}
      {mobileMenuOpen && (
        <div className="lg:hidden border-b border-white/10 bg-slate-950 px-4 py-3 space-y-1">
          {navLinks.map((link) => {
            const Icon = link.icon;
            const isActive = pathname === link.href;
            return (
              <Link
                key={link.href}
                href={link.href}
                onClick={() => setMobileMenuOpen(false)}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium ${
                  isActive
                    ? "bg-blue-600/15 text-blue-400 border border-blue-500/30"
                    : "text-slate-400 hover:bg-slate-900 hover:text-white"
                }`}
              >
                <Icon className="w-4 h-4" />
                {link.label}
              </Link>
            );
          })}
        </div>
      )}
    </header>
  );
}
