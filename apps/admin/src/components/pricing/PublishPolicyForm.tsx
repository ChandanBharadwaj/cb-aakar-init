"use client";

import { useEffect, useMemo, useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { AdminMaterial, PriceBreakdown as Breakdown, PricingPolicy, PricingPolicyVersion, Problem } from "@/lib/api/types";
import { paiseToRupees, rupeesToPaise } from "@/lib/format";
import { useCanWrite } from "@/store/session";
import { Field } from "@/components/ui/Field";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { PriceBreakdown } from "@/components/ui/PriceBreakdown";
import { ProblemCard } from "@/components/ui/ProblemCard";

interface Draft {
  version: string;
  note: string;
  machine_rate: string;
  finishing: { cls: string; fee: string }[];
  packaging_fee: string;
  margin_pct: string;
  ending: string;
  shipping_flat: string;
  free_above: string;
  shipping_label: string;
}

function suggestVersion(existing: string[]): string {
  const d = new Date();
  const base = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  for (let n = 1; n < 100; n += 1) {
    const v = `${base}-v${n}`;
    if (!existing.includes(v)) return v;
  }
  return `${base}-${Date.now()}`;
}

function fromPolicy(p: PricingPolicy, versions: string[]): Draft {
  return {
    version: suggestVersion(versions),
    note: "",
    machine_rate: paiseToRupees(p.machine_rate_paise_per_hour),
    finishing: Object.entries(p.finishing_fee_paise).map(([cls, fee]) => ({ cls, fee: paiseToRupees(fee) })),
    packaging_fee: paiseToRupees(p.packaging_fee_paise),
    margin_pct: String(p.margin_pct),
    ending: String(p.round_to_rupees_ending_in),
    shipping_flat: paiseToRupees(p.shipping_flat_paise),
    free_above: paiseToRupees(p.free_shipping_above_paise),
    shipping_label: p.shipping_label,
  };
}

function toPolicy(d: Draft): PricingPolicy {
  return {
    machine_rate_paise_per_hour: rupeesToPaise(d.machine_rate),
    finishing_fee_paise: Object.fromEntries(d.finishing.filter((f) => f.cls.trim()).map((f) => [f.cls.trim(), rupeesToPaise(f.fee)])),
    packaging_fee_paise: rupeesToPaise(d.packaging_fee),
    margin_pct: Number.parseFloat(d.margin_pct) || 0,
    round_to_rupees_ending_in: Math.min(9, Math.max(0, Number.parseInt(d.ending, 10) || 0)),
    shipping_flat_paise: rupeesToPaise(d.shipping_flat),
    free_shipping_above_paise: rupeesToPaise(d.free_above),
    shipping_label: d.shipping_label,
  };
}

export interface PublishPolicyFormProps {
  active: PricingPolicyVersion;
  versions: PricingPolicyVersion[];
  materials: AdminMaterial[];
  onPublished(created: PricingPolicyVersion): void;
}

/** Prefilled from the active policy; a live preview prices a sample piece under the draft. Owner-only publish. */
export function PublishPolicyForm({ active, versions, materials, onPublished }: PublishPolicyFormProps) {
  const canWrite = useCanWrite();
  const versionIds = useMemo(() => versions.map((v) => v.version), [versions]);
  const [draft, setDraft] = useState<Draft>(() => fromPolicy(active.policy, versionIds));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const [published, setPublished] = useState<string>();

  // Sample piece for the preview: the Jharokha stand from the Checkout board.
  const [material, setMaterial] = useState(() => materials.find((m) => m.id === "terracotta_silk")?.id ?? materials[0]?.id ?? "");
  const [volume, setVolume] = useState("67.7");
  const [seconds, setSeconds] = useState("13200");
  const [preview, setPreview] = useState<Breakdown>();
  const [previewProblem, setPreviewProblem] = useState<Problem>();
  const [previewing, setPreviewing] = useState(false);

  const policy = useMemo(() => toPolicy(draft), [draft]);
  const previewKey = JSON.stringify({ policy, material, volume, seconds });

  useEffect(() => {
    const vol = Number.parseFloat(volume);
    const secs = Number.parseInt(seconds, 10);
    if (!material || !Number.isFinite(vol) || vol <= 0 || !Number.isFinite(secs) || secs <= 0) return;
    let cancelled = false;
    const t = setTimeout(async () => {
      setPreviewing(true);
      try {
        const res = await api.pricing.preview({ policy, material, extruded_volume_cm3: vol, print_seconds: secs });
        if (!cancelled) {
          setPreview(res);
          setPreviewProblem(undefined);
        }
      } catch (err) {
        if (!cancelled) setPreviewProblem(toProblem(err));
      } finally {
        if (!cancelled) setPreviewing(false);
      }
    }, 350);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
    // previewKey encodes policy, material, volume and seconds.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [previewKey]);

  function set<K extends keyof Draft>(k: K, v: Draft[K]) {
    setDraft((d) => ({ ...d, [k]: v }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canWrite) return;
    setBusy(true);
    setProblem(undefined);
    setPublished(undefined);
    try {
      const created = await api.pricing.publish({ version: draft.version.trim(), policy, note: draft.note.trim() || undefined });
      setPublished(created.version);
      setDraft(fromPolicy(created.policy, [...versionIds, created.version]));
      onPublished(created);
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(false);
    }
  }

  const versionTaken = versionIds.includes(draft.version.trim());
  const versionOk = /^[A-Za-z0-9._-]{3,40}$/.test(draft.version.trim());
  const dis = busy || !canWrite;

  return (
    <section className="ak-card grid gap-5 p-5" aria-labelledby="publish-heading">
      <div className="grid gap-1">
        <h2 id="publish-heading" className="font-display text-2xl font-semibold">
          Publish a new version
        </h2>
        <p className="text-sm text-surface-muted">Prefilled from the active policy. Publishing makes the new version active at once; carts and orders keep the version they were priced with (ADR-0008).</p>
        <OwnerOnlyHint what="Publishing policies" />
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_300px]">
        <form onSubmit={submit} className="grid gap-4" aria-busy={busy}>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Version id" hint="3–40 characters: letters, digits, dot, dash, underscore" error={draft.version && !versionOk ? "Not a valid version id" : versionTaken ? "This version already exists" : undefined}>
              {(id) => <input id={id} className="ak-input ak-input-sm font-mono" value={draft.version} onChange={(e) => set("version", e.target.value)} disabled={dis} required />}
            </Field>
            <Field label="Note" hint="Why this version exists (up to 200 characters)">
              {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={200} value={draft.note} onChange={(e) => set("note", e.target.value)} disabled={dis} placeholder="Measured filament cost, Sept" />}
            </Field>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Machine rate (₹ per hour)">{(id) => <input id={id} type="number" min="0" step="1" className="ak-input ak-input-sm" value={draft.machine_rate} onChange={(e) => set("machine_rate", e.target.value)} disabled={dis} required />}</Field>
            <Field label="Packaging fee (₹)">{(id) => <input id={id} type="number" min="0" step="1" className="ak-input ak-input-sm" value={draft.packaging_fee} onChange={(e) => set("packaging_fee", e.target.value)} disabled={dis} required />}</Field>
            <Field label="Margin (%)" hint="0–500, applied to the sum of the lines">{(id) => <input id={id} type="number" min="0" max="500" step="0.5" className="ak-input ak-input-sm" value={draft.margin_pct} onChange={(e) => set("margin_pct", e.target.value)} disabled={dis} required />}</Field>
            <Field label="Round to rupees ending in" hint="9 turns ₹1,243 into ₹1,249">{(id) => <input id={id} type="number" min="0" max="9" step="1" className="ak-input ak-input-sm" value={draft.ending} onChange={(e) => set("ending", e.target.value)} disabled={dis} required />}</Field>
            <Field label="Shipping flat rate (₹)">{(id) => <input id={id} type="number" min="0" step="1" className="ak-input ak-input-sm" value={draft.shipping_flat} onChange={(e) => set("shipping_flat", e.target.value)} disabled={dis} required />}</Field>
            <Field label="Free shipping from (₹)" hint="Subtotal at or above this ships free">{(id) => <input id={id} type="number" min="0" step="1" className="ak-input ak-input-sm" value={draft.free_above} onChange={(e) => set("free_above", e.target.value)} disabled={dis} required />}</Field>
            <Field label="Shipping label" hint="Shown on the price breakdown (up to 60 characters)" className="sm:col-span-2">
              {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={60} value={draft.shipping_label} onChange={(e) => set("shipping_label", e.target.value)} disabled={dis} required />}
            </Field>
          </div>

          <fieldset className="grid gap-2">
            <legend className="ak-label mb-1.5">Finishing fee per finish class (₹)</legend>
            {draft.finishing.map((f, i) => (
              <div key={i} className="grid grid-cols-[1fr_1fr_auto] gap-2">
                <input aria-label="Finish class" className="ak-input ak-input-sm font-mono" value={f.cls} onChange={(e) => set("finishing", draft.finishing.map((x, j) => (j === i ? { ...x, cls: e.target.value } : x)))} disabled={dis} placeholder="matte" />
                <input aria-label="Fee in rupees" type="number" min="0" step="1" className="ak-input ak-input-sm" value={f.fee} onChange={(e) => set("finishing", draft.finishing.map((x, j) => (j === i ? { ...x, fee: e.target.value } : x)))} disabled={dis} />
                <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => set("finishing", draft.finishing.filter((_, j) => j !== i))} disabled={dis || draft.finishing.length <= 1} aria-label={`Remove ${f.cls || "row"}`}>
                  Remove
                </button>
              </div>
            ))}
            <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm justify-self-start" onClick={() => set("finishing", [...draft.finishing, { cls: "", fee: "0" }])} disabled={dis}>
              Add finish class
            </button>
          </fieldset>

          {problem && <ProblemCard compact problem={problem} title={problem.code === "policy_version_exists" ? "Version id already used" : problem.code === "forbidden" ? "Owner only" : undefined} />}
          {published && (
            <p className="ak-well p-3 text-sm" role="status">
              Published <span className="font-mono font-semibold">{published}</span>; it is the active policy now.
            </p>
          )}
          <div className="flex flex-wrap items-center gap-3">
            <button type="submit" className="ak-btn ak-btn-primary" disabled={dis || !versionOk || versionTaken} title={canWrite ? undefined : "Owner only"}>
              {busy ? "Publishing…" : "Publish and activate"}
            </button>
            <button type="button" className="ak-btn ak-btn-secondary" onClick={() => setDraft(fromPolicy(active.policy, versionIds))} disabled={busy}>
              Reset to active
            </button>
          </div>
        </form>

        <aside className="grid content-start gap-3">
          <div className="grid gap-1">
            <span className="ak-label">Preview under this draft</span>
            <p className="text-[11px] text-surface-muted">Sample piece priced live by POST /pricing/preview as you type. Defaults are the Jharokha stand from the Checkout board.</p>
          </div>
          <Field label="Material">
            {(id) => (
              <select id={id} className="ak-input ak-input-sm" value={material} onChange={(e) => setMaterial(e.target.value)}>
                {materials.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name} · {m.finish_class}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <div className="grid grid-cols-2 gap-2">
            <Field label="Extruded volume (cm³)">{(id) => <input id={id} type="number" min="0.1" step="0.1" className="ak-input ak-input-sm" value={volume} onChange={(e) => setVolume(e.target.value)} />}</Field>
            <Field label="Print time (s)">{(id) => <input id={id} type="number" min="1" step="60" className="ak-input ak-input-sm" value={seconds} onChange={(e) => setSeconds(e.target.value)} />}</Field>
          </div>
          <PriceBreakdown price={preview} loading={previewing} error={previewProblem?.detail ?? previewProblem?.title} footnote={preview ? `${preview.mass_g} g · policy: this draft` : undefined} />
          {previewProblem && <ProblemCard compact problem={previewProblem} />}
        </aside>
      </div>
    </section>
  );
}
