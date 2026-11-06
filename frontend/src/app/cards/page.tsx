"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { Navbar } from "@/components/Navbar";
import { api } from "@/lib/api";
import { AccountBalance, VirtualCard, CardDetails } from "@/types";
import { 
  CreditCard, 
  ShieldCheck, 
  Eye, 
  EyeOff, 
  Copy, 
  Check, 
  Lock, 
  Unlock, 
  PlusCircle, 
  RefreshCw, 
  CheckCircle2, 
  AlertCircle, 
  Sliders, 
  Globe2, 
  ShoppingBag,
  Zap,
  X
} from "lucide-react";

export default function CardsPage() {
  const { user, isLoading } = useAuth();
  const router = useRouter();

  const [cards, setCards] = useState<VirtualCard[]>([]);
  const [selectedCardIdx, setSelectedCardIdx] = useState(0);
  const [accounts, setAccounts] = useState<AccountBalance[]>([]);
  const [revealedDetails, setRevealedDetails] = useState<CardDetails | null>(null);
  const [showSensitive, setShowSensitive] = useState(false);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);
  const [updatingStatus, setUpdatingStatus] = useState(false);

  // Issue Card Modal
  const [issueModalOpen, setIssueModalOpen] = useState(false);
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const [cardholderName, setCardholderName] = useState("");
  const [cardBrand, setCardBrand] = useState<"VISA" | "MASTERCARD">("VISA");
  const [monthlyLimit, setMonthlyLimit] = useState("5000");
  const [issuing, setIssuing] = useState(false);
  const [issueSuccess, setIssueSuccess] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/");
    } else if (user) {
      loadData();
    }
  }, [user, isLoading]);

  const loadData = async () => {
    try {
      const cardsRes = await api.get<VirtualCard[]>("/cards/me");
      setCards(cardsRes.data || []);

      if (user?.id) {
        const accRes = await api.get<AccountBalance[]>(`/accounts/user/${user.id}`);
        setAccounts(accRes.data || []);
        if (accRes.data?.length > 0 && !selectedAccountId) {
          setSelectedAccountId(accRes.data[0].id || accRes.data[0].accountId);
        }
      }
    } catch (e) {
      console.error("Failed to load cards", e);
    }
  };

  const currentCard = cards[selectedCardIdx] || cards[0];

  const handleToggleReveal = async () => {
    if (showSensitive) {
      setShowSensitive(false);
      return;
    }

    if (!currentCard) return;

    if (revealedDetails && revealedDetails.id === currentCard.id) {
      setShowSensitive(true);
      return;
    }

    setLoadingDetails(true);
    try {
      const res = await api.get<CardDetails>(`/cards/${currentCard.id}/details`);
      setRevealedDetails(res.data);
      setShowSensitive(true);
    } catch (err: any) {
      console.error("Failed to reveal card details", err);
    } finally {
      setLoadingDetails(false);
    }
  };

  const handleToggleFreeze = async () => {
    if (!currentCard) return;
    setUpdatingStatus(true);
    const newStatus = currentCard.status === "ACTIVE" ? "FROZEN" : "ACTIVE";

    try {
      const res = await api.patch<VirtualCard>(`/cards/${currentCard.id}/status`, {
        status: newStatus,
      });
      setCards((prev) =>
        prev.map((c) => (c.id === currentCard.id ? res.data : c))
      );
    } catch (err: any) {
      console.error("Failed to update status", err);
    } finally {
      setUpdatingStatus(false);
    }
  };

  const handleToggleControls = async (field: "online" | "international") => {
    if (!currentCard) return;

    const payload = {
      onlinePaymentsEnabled: field === "online" ? !currentCard.onlinePaymentsEnabled : currentCard.onlinePaymentsEnabled,
      internationalEnabled: field === "international" ? !currentCard.internationalEnabled : currentCard.internationalEnabled,
    };

    try {
      const res = await api.patch<VirtualCard>(`/cards/${currentCard.id}/controls`, payload);
      setCards((prev) =>
        prev.map((c) => (c.id === currentCard.id ? res.data : c))
      );
    } catch (err: any) {
      console.error("Failed to update controls", err);
    }
  };

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopied(label);
    setTimeout(() => setCopied(null), 2000);
  };

  const handleIssueCard = async (e: React.FormEvent) => {
    e.preventDefault();
    setIssuing(true);
    setIssueSuccess(null);
    setErrorMessage(null);

    try {
      const res = await api.post<VirtualCard>("/cards/issue", {
        accountId: selectedAccountId,
        cardholderName: cardholderName || `${user?.firstName} ${user?.lastName}`,
        cardBrand,
        monthlyLimit: parseFloat(monthlyLimit),
      });

      setIssueSuccess(`Card ending in ${res.data.lastFour} issued successfully!`);
      setTimeout(() => {
        setIssueModalOpen(false);
        setIssueSuccess(null);
      }, 1200);
      loadData();
    } catch (err: any) {
      setErrorMessage(err.response?.data?.detail || "Failed to issue card");
    } finally {
      setIssuing(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#060911] text-slate-100 flex flex-col font-sans selection:bg-blue-600 selection:text-white">
      <Navbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/20 text-blue-400 text-xs font-mono font-medium mb-2">
              <CreditCard className="w-3.5 h-3.5" />
              <span>Multi-Currency Visa & Mastercard Issuing</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
              Virtual Debit Cards & Controls
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Instant card issuance connected directly to your ledger wallets with real-time freeze, spend limits, and CVV reveal.
            </p>
          </div>

          <button
            onClick={() => {
              setCardholderName(`${user?.firstName || ""} ${user?.lastName || ""}`.trim());
              setIssueModalOpen(true);
            }}
            className="flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold shadow-lg shadow-blue-600/25 transition-all self-start sm:self-auto"
          >
            <PlusCircle className="w-4 h-4" />
            <span>Issue New Card</span>
          </button>
        </div>

        {cards.length === 0 ? (
          /* Empty State: Issue First Card */
          <div className="p-12 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl text-center space-y-4 max-w-lg mx-auto">
            <div className="h-16 w-16 rounded-3xl bg-blue-600/20 text-blue-400 border border-blue-500/30 flex items-center justify-center mx-auto">
              <CreditCard className="w-8 h-8" />
            </div>
            <h3 className="text-lg font-bold text-white">No Virtual Cards Issued Yet</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Issue an instant Visa or Mastercard debit card linked directly to your active currency wallet for secure online payments.
            </p>
            <button
              onClick={() => {
                setCardholderName(`${user?.firstName || ""} ${user?.lastName || ""}`.trim());
                setIssueModalOpen(true);
              }}
              className="px-6 py-3 rounded-2xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs shadow-xl shadow-blue-600/30 transition-all inline-flex items-center gap-2"
            >
              <PlusCircle className="w-4 h-4" />
              <span>Issue Your First Card</span>
            </button>
          </div>
        ) : (
          /* Main Card Visualizer and Controls */
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            
            {/* Left: Card Visual & Card Details */}
            <div className="lg:col-span-6 space-y-6">
              {/* Metallic 3D Virtual Card */}
              <div className={`relative rounded-3xl p-7 bg-gradient-to-tr ${
                currentCard?.status === "FROZEN"
                  ? "from-slate-900 via-slate-950 to-slate-900 opacity-70 grayscale"
                  : currentCard?.cardBrand === "VISA"
                  ? "from-slate-900 via-slate-950 to-blue-950 border-blue-500/30"
                  : "from-slate-900 via-slate-950 to-amber-950 border-amber-500/30"
              } border shadow-2xl overflow-hidden flex flex-col justify-between min-h-[260px] transition-all`}>
                
                {/* Glow & Texture */}
                <div className="absolute top-0 right-0 w-64 h-64 bg-blue-500/10 rounded-full blur-3xl pointer-events-none"></div>

                {/* Card Top: Chip + Brand */}
                <div className="flex items-center justify-between relative z-10">
                  <div className="flex items-center gap-3">
                    {/* Metallic Chip */}
                    <div className="h-8 w-11 rounded-lg bg-gradient-to-br from-amber-300 via-amber-400 to-amber-600 p-[1.5px] shadow-sm">
                      <div className="h-full w-full bg-amber-400/30 rounded-[6px] border border-amber-200/50 flex flex-col justify-between p-1">
                        <div className="h-px bg-amber-900/40 w-full"></div>
                        <div className="h-px bg-amber-900/40 w-full"></div>
                      </div>
                    </div>
                    {/* Contactless waves icon */}
                    <div className="text-slate-400 font-mono text-sm tracking-widest rotate-90">
                      )))
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    {currentCard?.status === "FROZEN" ? (
                      <span className="px-2.5 py-1 rounded-full bg-rose-500/20 text-rose-300 text-[10px] font-mono font-bold uppercase border border-rose-500/30 flex items-center gap-1">
                        <Lock className="w-3 h-3" /> Frozen
                      </span>
                    ) : (
                      <span className="px-2.5 py-1 rounded-full bg-emerald-500/20 text-emerald-300 text-[10px] font-mono font-bold uppercase border border-emerald-500/30 flex items-center gap-1">
                        <Zap className="w-3 h-3" /> Active
                      </span>
                    )}
                    <span className="font-extrabold text-base tracking-wider font-mono text-white">
                      {currentCard?.cardBrand}
                    </span>
                  </div>
                </div>

                {/* Card Number */}
                <div className="my-5 relative z-10">
                  <div className="text-[10px] font-mono text-slate-400 uppercase tracking-widest">Card Number</div>
                  <div className="text-xl sm:text-2xl font-mono font-black tracking-widest text-slate-100 mt-1 flex items-center gap-3">
                    {showSensitive && revealedDetails ? (
                      <span>{revealedDetails.cardNumber.match(/.{1,4}/g)?.join(" ")}</span>
                    ) : (
                      <span>•••• •••• •••• {currentCard?.lastFour}</span>
                    )}
                    <button
                      onClick={handleToggleReveal}
                      disabled={loadingDetails}
                      className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-slate-300 hover:text-white transition-colors"
                      title={showSensitive ? "Hide sensitive details" : "Reveal full card details"}
                    >
                      {loadingDetails ? (
                        <RefreshCw className="w-4 h-4 animate-spin text-blue-400" />
                      ) : showSensitive ? (
                        <EyeOff className="w-4 h-4" />
                      ) : (
                        <Eye className="w-4 h-4" />
                      )}
                    </button>
                    {showSensitive && revealedDetails && (
                      <button
                        onClick={() => copyToClipboard(revealedDetails.cardNumber, "PAN")}
                        className="text-slate-400 hover:text-white"
                        title="Copy Number"
                      >
                        {copied === "PAN" ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                      </button>
                    )}
                  </div>
                </div>

                {/* Card Bottom: Holder, Expiry, CVV */}
                <div className="flex items-end justify-between relative z-10 text-xs font-mono">
                  <div>
                    <div className="text-[10px] text-slate-400 uppercase tracking-wider">Cardholder</div>
                    <div className="text-sm font-bold text-slate-200 uppercase tracking-wide mt-0.5">
                      {currentCard?.cardholderName}
                    </div>
                  </div>

                  <div className="flex items-center gap-6">
                    <div>
                      <div className="text-[10px] text-slate-400 uppercase tracking-wider">Expires</div>
                      <div className="text-sm font-bold text-slate-200 mt-0.5">
                        {String(currentCard?.expiryMonth).padStart(2, "0")}/{String(currentCard?.expiryYear).slice(-2)}
                      </div>
                    </div>

                    <div>
                      <div className="text-[10px] text-slate-400 uppercase tracking-wider">CVV</div>
                      <div className="text-sm font-bold text-slate-200 mt-0.5 flex items-center gap-1.5">
                        {showSensitive && revealedDetails ? (
                          <>
                            <span className="text-emerald-400">{revealedDetails.cvv}</span>
                            <button
                              onClick={() => copyToClipboard(revealedDetails.cvv, "CVV")}
                              className="text-slate-400 hover:text-white"
                            >
                              {copied === "CVV" ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                            </button>
                          </>
                        ) : (
                          <span>•••</span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Card Switcher Pill List (if user has multiple cards) */}
              {cards.length > 1 && (
                <div className="flex items-center gap-2 overflow-x-auto pb-1">
                  {cards.map((c, idx) => (
                    <button
                      key={c.id}
                      onClick={() => {
                        setSelectedCardIdx(idx);
                        setShowSensitive(false);
                      }}
                      className={`px-3 py-1.5 rounded-xl text-xs font-mono font-semibold transition-all shrink-0 ${
                        idx === selectedCardIdx
                          ? "bg-blue-600 text-white shadow-md shadow-blue-600/30"
                          : "bg-slate-900 border border-white/5 text-slate-400 hover:text-white"
                      }`}
                    >
                      {c.cardBrand} •••• {c.lastFour} ({c.currency})
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Right: Security & Spending Controls */}
            <div className="lg:col-span-6 space-y-6">
              <div className="p-6 sm:p-7 rounded-3xl border border-white/10 bg-slate-900/60 backdrop-blur-xl shadow-xl space-y-6">
                <div className="flex items-center justify-between border-b border-white/10 pb-4">
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-xl bg-indigo-600/20 text-indigo-400 border border-indigo-500/30 flex items-center justify-center">
                      <Sliders className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-white text-base">Security & Spend Controls</h3>
                      <p className="text-xs text-slate-400">Linked to Account: {currentCard?.accountNumber}</p>
                    </div>
                  </div>

                  <button
                    onClick={handleToggleFreeze}
                    disabled={updatingStatus}
                    className={`flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                      currentCard?.status === "ACTIVE"
                        ? "bg-rose-500/20 text-rose-300 border border-rose-500/30 hover:bg-rose-500/30"
                        : "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 hover:bg-emerald-500/30"
                    }`}
                  >
                    {updatingStatus ? (
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    ) : currentCard?.status === "ACTIVE" ? (
                      <>
                        <Lock className="w-3.5 h-3.5" />
                        <span>Freeze Card</span>
                      </>
                    ) : (
                      <>
                        <Unlock className="w-3.5 h-3.5" />
                        <span>Unfreeze Card</span>
                      </>
                    )}
                  </button>
                </div>

                {/* Spending Limit Display */}
                <div className="p-4 rounded-2xl bg-slate-950/70 border border-white/5 space-y-2">
                  <div className="flex items-center justify-between text-xs text-slate-400">
                    <span className="font-semibold uppercase tracking-wider">Monthly Spend Limit</span>
                    <span className="font-mono font-bold text-white text-sm">
                      ${parseFloat(currentCard?.monthlyLimit?.toString() || "5000").toLocaleString()} {currentCard?.currency}
                    </span>
                  </div>
                  <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">
                    <div className="bg-blue-500 h-full w-[28%] rounded-full"></div>
                  </div>
                  <div className="text-[11px] font-mono text-slate-500 flex justify-between">
                    <span>$0.00 spent this month</span>
                    <span>100% available</span>
                  </div>
                </div>

                {/* Feature Toggles */}
                <div className="space-y-3">
                  <div className="p-4 rounded-2xl bg-slate-950/70 border border-white/5 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="h-8 w-8 rounded-lg bg-blue-600/20 text-blue-400 flex items-center justify-center">
                        <ShoppingBag className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="text-xs font-bold text-white">Online E-Commerce Payments</div>
                        <div className="text-[11px] text-slate-400">Allow 3D Secure web transactions</div>
                      </div>
                    </div>
                    <button
                      onClick={() => handleToggleControls("online")}
                      className={`w-12 h-6 rounded-full transition-colors relative p-0.5 ${
                        currentCard?.onlinePaymentsEnabled ? "bg-blue-600" : "bg-slate-800"
                      }`}
                    >
                      <div
                        className={`w-5 h-5 rounded-full bg-white transition-transform ${
                          currentCard?.onlinePaymentsEnabled ? "translate-x-6" : "translate-x-0"
                        }`}
                      ></div>
                    </button>
                  </div>

                  <div className="p-4 rounded-2xl bg-slate-950/70 border border-white/5 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="h-8 w-8 rounded-lg bg-cyan-600/20 text-cyan-400 flex items-center justify-center">
                        <Globe2 className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="text-xs font-bold text-white">International Transactions</div>
                        <div className="text-[11px] text-slate-400">Cross-border merchant charges</div>
                      </div>
                    </div>
                    <button
                      onClick={() => handleToggleControls("international")}
                      className={`w-12 h-6 rounded-full transition-colors relative p-0.5 ${
                        currentCard?.internationalEnabled ? "bg-blue-600" : "bg-slate-800"
                      }`}
                    >
                      <div
                        className={`w-5 h-5 rounded-full bg-white transition-transform ${
                          currentCard?.internationalEnabled ? "translate-x-6" : "translate-x-0"
                        }`}
                      ></div>
                    </button>
                  </div>
                </div>

                {/* Card Safety Notice */}
                <div className="p-4 rounded-2xl bg-indigo-950/20 border border-indigo-500/20 text-indigo-300 text-xs flex items-center gap-2.5">
                  <ShieldCheck className="w-4 h-4 shrink-0 text-indigo-400" />
                  <span>Card numbers are encrypted and protected by strict session authentication.</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Issue Card Modal */}
        {issueModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-white/15 rounded-3xl p-6 sm:p-7 max-w-md w-full shadow-2xl relative animate-in fade-in zoom-in-95 duration-200">
              <div className="flex items-center justify-between pb-4 border-b border-white/10">
                <div className="flex items-center gap-3">
                  <div className="h-10 w-10 rounded-xl bg-blue-600/20 text-blue-400 border border-blue-500/30 flex items-center justify-center">
                    <CreditCard className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-bold text-white text-base">Issue Virtual Debit Card</h3>
                    <p className="text-xs text-slate-400">Instant PAN & CVV creation</p>
                  </div>
                </div>
                <button
                  onClick={() => setIssueModalOpen(false)}
                  className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {errorMessage && (
                <div className="mt-4 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs">
                  {errorMessage}
                </div>
              )}

              <form onSubmit={handleIssueCard} className="mt-5 space-y-4">
                <div>
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider block mb-1.5">
                    Linked Wallet Account
                  </label>
                  <select
                    value={selectedAccountId}
                    onChange={(e) => setSelectedAccountId(e.target.value)}
                    className="w-full px-4 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-xs focus:outline-none focus:border-blue-500"
                  >
                    {accounts.map((acc) => (
                      <option key={acc.id || acc.accountId} value={acc.id || acc.accountId}>
                        {acc.accountName} ({acc.accountNumber}) — {acc.currency}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider block mb-1.5">
                    Cardholder Name
                  </label>
                  <input
                    type="text"
                    required
                    value={cardholderName}
                    onChange={(e) => setCardholderName(e.target.value)}
                    className="w-full px-4 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-xs focus:outline-none focus:border-blue-500 uppercase"
                    placeholder="ALEX MORGAN"
                  />
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider block mb-1.5">
                    Card Brand
                  </label>
                  <div className="grid grid-cols-2 gap-3">
                    {(["VISA", "MASTERCARD"] as const).map((brand) => (
                      <button
                        type="button"
                        key={brand}
                        onClick={() => setCardBrand(brand)}
                        className={`p-3 rounded-xl border text-center font-mono font-bold text-xs transition-all ${
                          cardBrand === brand
                            ? "bg-blue-600/30 border-blue-500 text-white"
                            : "bg-slate-950 border-slate-800 text-slate-400 hover:border-slate-700"
                        }`}
                      >
                        {brand}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider block mb-1.5">
                    Monthly Spending Limit ($)
                  </label>
                  <input
                    type="number"
                    step="100"
                    min="100"
                    max="100000"
                    required
                    value={monthlyLimit}
                    onChange={(e) => setMonthlyLimit(e.target.value)}
                    className="w-full px-4 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-xs focus:outline-none focus:border-blue-500"
                    placeholder="5000"
                  />
                </div>

                {issueSuccess && (
                  <div className="p-3 rounded-xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 text-xs font-medium flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>{issueSuccess}</span>
                  </div>
                )}

                <div className="pt-3 flex items-center justify-end gap-3">
                  <button
                    type="button"
                    onClick={() => setIssueModalOpen(false)}
                    className="px-4 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:text-white transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={issuing}
                    className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold shadow-lg shadow-blue-600/30 flex items-center gap-2 disabled:opacity-50 transition-all"
                  >
                    {issuing ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Provisioning Card PAN...</span>
                      </>
                    ) : (
                      <>
                        <PlusCircle className="w-4 h-4" />
                        <span>Issue Card</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

