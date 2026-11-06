"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";

export interface Beneficiary {
  id: string;
  type: "WALLET" | "BANK";
  nickname: string;
  recipientAccountId: string | null;
  bankCode: string | null;
  accountNumber: string | null;
  accountName: string;
  currency: string;
}

type Destination = {
  recipientAccountId?: string;
  bankCode?: string;
  accountNumber?: string;
  accountName?: string;
};

export function BeneficiaryPicker({ type, currency, destination, onSelect }: {
  type: Beneficiary["type"];
  currency: string;
  destination: Destination;
  onSelect: (beneficiary: Beneficiary) => void;
}) {
  const [items, setItems] = useState<Beneficiary[]>([]);
  const [selected, setSelected] = useState("");
  const [nickname, setNickname] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const refresh = async () => {
    const response = await api.get<Beneficiary[]>("/beneficiaries");
    setItems(response.data);
  };
  useEffect(() => {
    refresh().catch(() => setError("Could not load saved recipients. You can still enter a destination manually."));
  }, []);
  useEffect(() => { setSelected(""); }, [currency]);
  const current = items.find(item => item.id === selected);
  const matches = current && (type === "WALLET"
    ? current.recipientAccountId === destination.recipientAccountId
    : current.bankCode === destination.bankCode && current.accountNumber === destination.accountNumber && current.accountName === destination.accountName);
  const run = async (action: () => Promise<void>) => {
    setBusy(true); setError(""); setNotice("");
    try { await action(); await refresh(); }
    catch (err: unknown) {
      const response = err as { response?: { data?: { detail?: string } } };
      setError(response.response?.data?.detail || "Could not update saved recipients.");
    } finally { setBusy(false); }
  };
  const style = "rounded-xl border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white";
  return <section className="space-y-3 rounded-2xl border border-white/10 p-4" aria-label="Saved beneficiaries">
    <label className="block text-sm text-slate-300" htmlFor={`beneficiary-${type}`}>Saved recipients ({currency})</label>
    <select id={`beneficiary-${type}`} className={`${style} w-full`} disabled={busy} value={matches ? selected : ""} onChange={e => {
      setSelected(e.target.value);
      const item = items.find(b => b.id === e.target.value);
      if (item) { setNickname(item.nickname); onSelect(item); }
    }}>
      <option value="">Enter a recipient manually</option>
      {items.filter(b => b.type === type && b.currency === currency).map(b =>
        <option key={b.id} value={b.id}>{b.nickname} — {b.accountName} {b.accountNumber ? `••••${b.accountNumber.slice(-4)}` : ""}</option>)}
    </select>
    <label className="block text-sm text-slate-300" htmlFor={`nickname-${type}`}>Recipient nickname</label>
    <input id={`nickname-${type}`} className={`${style} w-full`} value={nickname} maxLength={100} onChange={e => setNickname(e.target.value)} placeholder="e.g. Alex or Rent" />
    <div className="flex flex-wrap gap-3">
      <button type="button" className={style} disabled={busy || !nickname.trim()} onClick={() => run(async () => {
        await api.post("/beneficiaries", { type, currency, nickname: nickname.trim(), ...destination });
        setNotice("Recipient saved.");
      })}>Save entered recipient</button>
      {matches && <>
        <button type="button" className={style} disabled={busy || !nickname.trim()} onClick={() => run(async () => {
          await api.patch(`/beneficiaries/${selected}`, { nickname: nickname.trim() });
          setNotice("Recipient renamed.");
        })}>Rename</button>
        <button type="button" className={style} disabled={busy} onClick={() => run(async () => {
          await api.delete(`/beneficiaries/${selected}`); setSelected(""); setNickname("");
          setNotice("Saved recipient removed. The entered payment destination is unchanged.");
        })}>Remove saved recipient</button>
      </>}
    </div>
    {error && <p role="alert" className="text-sm text-rose-400">{error}</p>}
    {notice && <p role="status" className="text-sm text-emerald-400">{notice}</p>}
  </section>;
}
