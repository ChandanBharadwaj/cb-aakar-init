"use client";

import { useEffect, useId, useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { Address, AddressInput, Serviceability } from "@/lib/api/types";
import { PINCODE_RE, serviceabilityLabel } from "./serviceability";

export interface AddressFormProps {
  initial?: Address;
  /** Prefill for a new address, e.g. the signed-in phone. */
  defaultPhone?: string;
  onSaved(address: Address): void;
  onCancel(): void;
}

interface Draft {
  label: string;
  name: string;
  phone: string; // 10 digits
  line1: string;
  line2: string;
  city: string;
  state: string;
  pincode: string;
  is_default: boolean;
}

const STATES = [
  "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala",
  "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura",
  "Uttar Pradesh", "Uttarakhand", "West Bengal", "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Jammu and Kashmir",
  "Ladakh", "Lakshadweep", "Puducherry",
];

function toDraft(a: Address | undefined, defaultPhone?: string): Draft {
  const digits = (a?.phone ?? defaultPhone ?? "").replace(/^\+91/, "").replace(/\D/g, "").slice(-10);
  return {
    label: a?.label ?? "Home",
    name: a?.name ?? "",
    phone: digits,
    line1: a?.line1 ?? "",
    line2: a?.line2 ?? "",
    city: a?.city ?? "",
    state: a?.state ?? "",
    pincode: a?.pincode ?? "",
    is_default: a?.is_default ?? false,
  };
}

function validate(d: Draft): Partial<Record<keyof Draft, string>> {
  const e: Partial<Record<keyof Draft, string>> = {};
  if (!d.name.trim()) e.name = "Who should we address the parcel to?";
  if (!/^[6-9][0-9]{9}$/.test(d.phone)) e.phone = "A 10-digit mobile number, starting 6–9.";
  if (!d.line1.trim()) e.line1 = "House, street or landmark.";
  if (!d.city.trim()) e.city = "City or town.";
  if (!d.state.trim()) e.state = "State.";
  if (!PINCODE_RE.test(d.pincode)) e.pincode = "A 6-digit pincode.";
  return e;
}

/** Add or edit a delivery address; checks serviceability as the pincode is typed. */
export function AddressForm({ initial, defaultPhone, onSaved, onCancel }: AddressFormProps) {
  const [draft, setDraft] = useState<Draft>(() => toDraft(initial, defaultPhone));
  const [errors, setErrors] = useState<Partial<Record<keyof Draft, string>>>({});
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string>();
  const [service, setService] = useState<{ status: "idle" } | { status: "loading" } | { status: "ready"; result: Serviceability } | { status: "error" }>({ status: "idle" });
  const id = useId();

  // Serviceability as soon as the pincode looks complete.
  useEffect(() => {
    if (!PINCODE_RE.test(draft.pincode)) {
      setService({ status: "idle" });
      return;
    }
    let cancelled = false;
    setService({ status: "loading" });
    const t = setTimeout(() => {
      api.shipping
        .serviceability(draft.pincode)
        .then((result) => !cancelled && setService({ status: "ready", result }))
        .catch(() => !cancelled && setService({ status: "error" }));
    }, 350);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [draft.pincode]);

  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => {
    setDraft((d) => ({ ...d, [k]: v }));
    setErrors((e) => ({ ...e, [k]: undefined }));
  };

  async function save() {
    const e = validate(draft);
    setErrors(e);
    if (Object.values(e).some(Boolean)) return;
    setBusy(true);
    setProblem(undefined);
    const body: AddressInput = {
      label: draft.label.trim() || undefined,
      name: draft.name.trim(),
      phone: `+91${draft.phone}`,
      line1: draft.line1.trim(),
      line2: draft.line2.trim() || undefined,
      city: draft.city.trim(),
      state: draft.state.trim(),
      pincode: draft.pincode,
      is_default: draft.is_default,
    };
    try {
      const saved = initial ? await api.addresses.update(initial.id, body) : await api.addresses.create(body);
      onSaved(saved);
    } catch (err) {
      const p = toProblem(err);
      setProblem(p.detail ?? p.title ?? "Couldn't save this address.");
    } finally {
      setBusy(false);
    }
  }

  const svc = service.status === "ready" ? serviceabilityLabel(service.result) : undefined;

  const field = (k: keyof Draft, label: string, input: React.ReactNode, hint?: React.ReactNode) => (
    <div className="grid gap-1">
      <label htmlFor={`${id}-${k}`} className="ak-label">
        {label}
      </label>
      {input}
      {errors[k] ? (
        <p role="alert" className="text-[11px] text-danger">
          {errors[k]}
        </p>
      ) : (
        hint
      )}
    </div>
  );
  const cls = (k: keyof Draft) => `ak-input min-h-10 py-2 text-sm ${errors[k] ? "border-danger" : ""}`;

  return (
    <form
      className="ak-well grid gap-3.5 p-4"
      onSubmit={(e) => {
        e.preventDefault();
        void save();
      }}
      aria-label={initial ? "Edit address" : "New address"}
    >
      <div className="flex items-center justify-between">
        <span className="font-display text-lg font-semibold">{initial ? "Edit address" : "New address"}</span>
        <div className="flex gap-1.5" role="group" aria-label="Label">
          {["Home", "Office", "Other"].map((l) => (
            <button key={l} type="button" className="ak-chip min-h-8 px-2.5 py-1 text-[11px]" aria-pressed={draft.label === l} onClick={() => set("label", l)}>
              {l}
            </button>
          ))}
        </div>
      </div>
      <div className="grid gap-3.5 sm:grid-cols-2">
        {field("name", "Name", <input id={`${id}-name`} className={cls("name")} value={draft.name} onChange={(e) => set("name", e.target.value)} autoComplete="name" maxLength={80} />)}
        {field(
          "phone",
          "Phone",
          <div className={`flex items-stretch overflow-hidden rounded-control border bg-surface-card ${errors.phone ? "border-danger" : "border-surface-border"}`}>
            <span className="grid place-items-center border-r border-surface-border bg-surface-bg px-3 text-xs font-semibold text-surface-muted" aria-hidden="true">
              +91
            </span>
            <input
              id={`${id}-phone`}
              type="tel"
              inputMode="numeric"
              maxLength={10}
              autoComplete="tel-national"
              value={draft.phone}
              onChange={(e) => set("phone", e.target.value.replace(/\D/g, "").slice(0, 10))}
              className="min-w-0 flex-1 bg-transparent px-3 py-2 text-sm outline-none"
            />
          </div>,
        )}
      </div>
      {field("line1", "Address", <input id={`${id}-line1`} className={cls("line1")} value={draft.line1} onChange={(e) => set("line1", e.target.value)} autoComplete="address-line1" placeholder="14, Jubilee Hills Rd 36" maxLength={120} />)}
      {field("line2", "Landmark (optional)", <input id={`${id}-line2`} className={cls("line2")} value={draft.line2} onChange={(e) => set("line2", e.target.value)} autoComplete="address-line2" maxLength={120} />)}
      <div className="grid gap-3.5 sm:grid-cols-[1fr_1fr_130px]">
        {field("city", "City", <input id={`${id}-city`} className={cls("city")} value={draft.city} onChange={(e) => set("city", e.target.value)} autoComplete="address-level2" maxLength={60} />)}
        {field(
          "state",
          "State",
          <>
            <input id={`${id}-state`} className={cls("state")} value={draft.state} onChange={(e) => set("state", e.target.value)} autoComplete="address-level1" list={`${id}-states`} maxLength={60} />
            <datalist id={`${id}-states`}>
              {STATES.map((s) => (
                <option key={s} value={s} />
              ))}
            </datalist>
          </>,
        )}
        {field(
          "pincode",
          "Pincode",
          <input id={`${id}-pincode`} className={cls("pincode")} inputMode="numeric" maxLength={6} value={draft.pincode} onChange={(e) => set("pincode", e.target.value.replace(/\D/g, "").slice(0, 6))} autoComplete="postal-code" />,
          service.status === "loading" ? (
            <p className="text-[11px] text-surface-muted">Checking delivery…</p>
          ) : svc ? (
            <p className={`text-[11px] font-semibold ${svc.ok ? "text-success" : "text-danger"}`} role="status">
              {svc.text}
              {svc.mock && <span className="ml-1 font-normal text-surface-muted">(mock)</span>}
            </p>
          ) : service.status === "error" ? (
            <p className="text-[11px] text-surface-muted">Couldn&apos;t check delivery just now.</p>
          ) : undefined,
        )}
      </div>
      <label className="flex items-center gap-2 text-xs text-surface-muted">
        <input type="checkbox" checked={draft.is_default} onChange={(e) => set("is_default", e.target.checked)} className="h-4 w-4 accent-[var(--ak-accent)]" />
        Use as my default address
      </label>
      {problem && (
        <p role="alert" className="text-xs text-danger">
          {problem}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="ak-btn ak-btn-primary min-h-10 text-xs" disabled={busy} aria-busy={busy}>
          {busy ? "Saving…" : "Save address"}
        </button>
        <button type="button" className="ak-btn ak-btn-secondary min-h-10 text-xs" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </form>
  );
}
