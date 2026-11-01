"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { ShieldCheck, Wallet, ArrowLeftRight, Landmark, FileCheck2, LogOut, ShieldAlert } from "lucide-react";

export function Navbar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  if (!user) return null;

  const isAdmin = user.role === "ROLE_ADMIN";

  const navLinks = [
    { href: "/dashboard", label: "Wallets", icon: Wallet },
    { href: "/transfers", label: "P2P Transfers", icon: ArrowLeftRight },
    { href: "/payouts", label: "Payouts", icon: Landmark },
    { href: "/kyc", label: "KYC Center", icon: FileCheck2 },
    ...(isAdmin ? [{ href: "/admin", label: "Admin & Reconciliation", icon: ShieldAlert }] : []),
  ];

  return (
    <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        <div className="flex items-center gap-8">
          <Link href="/dashboard" className="flex items-center gap-2">
            <div className="h-9 w-9 rounded-lg bg-blue-600 flex items-center justify-center font-bold text-white shadow-lg shadow-blue-500/20">
              ⚡
            </div>
            <span className="font-semibold text-lg tracking-tight bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">
              FintechCore
            </span>
          </Link>

          <nav className="hidden md:flex items-center gap-1">
            {navLinks.map((link) => {
              const Icon = link.icon;
              const isActive = pathname === link.href;
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? "bg-slate-800 text-blue-400"
                      : "text-slate-400 hover:text-slate-200 hover:bg-slate-800/50"
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  {link.label}
                </Link>
              );
            })}
          </nav>
        </div>

        <div className="flex items-center gap-4">
          <div className="hidden sm:flex items-center gap-2 px-2.5 py-1 rounded-full border border-slate-700 bg-slate-800/50 text-xs">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
            <span className="text-slate-300 font-medium">Tier {user.kycTier}</span>
            <span className="text-slate-500">•</span>
            <span className="text-slate-400">Limit: ${user.dailyLimit.toLocaleString()}</span>
          </div>

          <div className="flex items-center gap-3">
            <div className="text-right hidden sm:block">
              <div className="text-sm font-medium text-slate-200 leading-tight">
                {user.firstName} {user.lastName}
              </div>
              <div className="text-xs text-slate-400 truncate max-w-[150px]">{user.email}</div>
            </div>

            <button
              onClick={logout}
              title="Logout"
              className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}

