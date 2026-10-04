"use client";

import { useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { AdminHardware, AdminHardwareInput, Problem } from "@/lib/api/types";
import { paiseToRupees, rupeesToPaise } from "@/lib/format";
import { useCanWrite } from "@/store/session";
import { Drawer } from "@/components/ui/Drawer";
import { Field } from "@/components/ui/Field";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Switch } from "@/components/ui/Switch";

interface Draft {
  sku: string;
  name: string;
  /** Rupees as typed; the API stores integer paise. */
  cost: string;
  weight: string;
  supplier: string;
  url: string;
  notes: string;
  available: boolean;
}

function fromItem(h: AdminHardware | null): Draft {
  if (!h) return { sku: "", name: "", cost: "0", weight: "", supplier: "", url: "", notes: "", available: true };
  return {
    sku: h.sku,
    name: h.name,
    cost: paiseToRupees(h.unit_cost_paise),
    weight: h.weight_g === undefined ? "" : String(h.weight_g),
    supplier: h.supplier ?? "",
    url: h.url ?? "",
    notes: h.notes ?? "",
    available: h.available,
  };
}

/** Row → input body (drops `updated_at`); used by the list's availability switch too. */
export function toInput(h: AdminHardware): AdminHardwareInput {
  return {
    sku: h.sku,
    name: h.name,
    unit_cost_paise: h.unit_cost_paise,
    ...(h.weight_g !== undefined ? { weight_g: h.weight_g } : {}),
    ...(h.supplier ? { supplier: h.supplier } : {}),
    ...(h.url ? { url: h.url } : {}),
    ...(h.notes ? { notes: h.notes } : {}),
    available: h.available,
  };
}

function fromDraft(d: Draft): AdminHardwareInput {
  const weight = Number.parseFloat(d.weight);
  const body: AdminHardwareInput = { sku: d.sku.trim(), name: d.name.trim(), unit_cost_paise: rupeesToPaise(d.cost), available: d.available };
  if (Number.isFinite(weight) && weight >= 0) body.weight_g = weight;
  if (d.supplier.trim()) body.supplier = d.supplier.trim();
  if (d.url.trim()) body.url = d.url.trim();
  if (d.notes.trim()) body.notes = d.notes.trim();
  return body;
}

const PROBLEM_TITLES: Record<string, string> = {
  hardware_exists: "That SKU is taken",
  validation_failed: "The API rejected a field",
  forbidden: "Owner only",
};

export interface HardwareDrawerProps {
  /** Existing item to edit, `null` for "Add hardware", `undefined` when closed. */
  item: AdminHardware | null | undefined;
  onClose(): void;
  onSaved(item: AdminHardware): void;
}

export function HardwareDrawer({ item, onClose, onSaved }: HardwareDrawerProps) {
  const open = item !== undefined;
  return (
    <Drawer open={open} onClose={onClose} title={item ? item.name : "Add hardware"} eyebrow={item ? `Hardware · ${item.sku}` : "New hardware item"}>
      {open && <HardwareForm key={item?.sku ?? "new"} item={item} onClose={onClose} onSaved={onSaved} />}
    </Drawer>
  );
}

function HardwareForm({ item, onClose, onSaved }: { item: AdminHardware | null; onClose(): void; onSaved(h: AdminHardware): void }) {
  const canWrite = useCanWrite();
  const editing = item !== null;
  const [draft, setDraft] = useState<Draft>(() => fromItem(item));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const dis = busy || !canWrite;

  function set<K extends keyof Draft>(k: K, v: Draft[K]) {
    setDraft((d) => ({ ...d, [k]: v }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canWrite) return;
    setBusy(true);
    setProblem(undefined);
    const body = fromDraft(draft);
    try {
      const saved = editing ? await api.hardware.update(item.sku, body) : await api.hardware.create(body);
      onSaved(saved);
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-5" aria-busy={busy}>
      <OwnerOnlyHint what="Hardware changes" />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="SKU" hint="snake_case, fixed once created; referenced by Avatars and template descriptors">
          {(id) => <input id={id} className="ak-input ak-input-sm font-mono" pattern="^[a-z][a-z0-9_]*$" maxLength={40} value={draft.sku} onChange={(e) => set("sku", e.target.value)} disabled={dis || editing} required />}
        </Field>
        <Field label="Name" hint="Customer-facing, e.g. Steel split ring 25 mm (up to 120)">
          {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={120} value={draft.name} onChange={(e) => set("name", e.target.value)} disabled={dis} required />}
        </Field>
        <Field label="Unit cost (₹)" hint={`Stored as integer paise · ${formatPaise(rupeesToPaise(draft.cost))}; the policy's hardware markup is applied on top`}>
          {(id) => <input id={id} type="number" min="0" step="0.01" className="ak-input ak-input-sm" value={draft.cost} onChange={(e) => set("cost", e.target.value)} disabled={dis} required />}
        </Field>
        <Field label="Weight (g)" hint="Per unit; recorded for the shipping adapter later">
          {(id) => <input id={id} type="number" min="0" step="0.1" className="ak-input ak-input-sm" value={draft.weight} onChange={(e) => set("weight", e.target.value)} disabled={dis} />}
        </Field>
        <Field label="Supplier" hint="Up to 120 characters">
          {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={120} value={draft.supplier} onChange={(e) => set("supplier", e.target.value)} disabled={dis} />}
        </Field>
        <Field label="Supplier URL" hint="Product page or quote (up to 500)">
          {(id) => <input id={id} type="url" className="ak-input ak-input-sm" maxLength={500} value={draft.url} onChange={(e) => set("url", e.target.value)} disabled={dis} placeholder="https://" />}
        </Field>
        <Field label="Notes" hint="Studio notes, e.g. pocket size or safety (up to 200)" className="sm:col-span-2">
          {(id) => <textarea id={id} className="ak-input" rows={2} maxLength={200} value={draft.notes} onChange={(e) => set("notes", e.target.value)} disabled={dis} />}
        </Field>
        <Field label="Available" hint="Off marks the part out of stock; Avatars that need it show a warning" inline className="sm:col-span-2">
          {(id) => <Switch id={id} label="Available" checked={draft.available} onChange={(v) => set("available", v)} disabled={dis} />}
        </Field>
      </div>
      {problem && <ProblemCard compact problem={problem} title={PROBLEM_TITLES[problem.code ?? ""]} />}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="ak-btn ak-btn-primary" disabled={dis} title={canWrite ? undefined : "Owner only"}>
          {busy ? "Saving…" : editing ? "Save hardware" : "Add hardware"}
        </button>
        <button type="button" className="ak-btn ak-btn-secondary" onClick={onClose} disabled={busy}>
          {canWrite ? "Cancel" : "Close"}
        </button>
      </div>
    </form>
  );
}
