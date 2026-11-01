import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "@/context/AuthContext";

export const metadata: Metadata = {
  title: "Fintech Core Payment Engine",
  description: "Double-Entry Ledger, P2P Transfers, Payouts & Reconciliation Dashboard",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-slate-950 text-slate-100 antialiased selection:bg-blue-600 selection:text-white">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}

