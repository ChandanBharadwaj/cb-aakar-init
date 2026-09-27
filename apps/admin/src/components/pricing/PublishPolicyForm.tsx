"use client";

import { useEffect, useMemo, useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { AdminFamily, AdminMaterial, FamilyRule, PriceBreakdown as Breakdown, PricingPolicy, PricingPolicyVersion, Problem } from "@/lib/api/types";
import { familyTitle, sortFamilies } from "@/lib/families";
import { paiseToRupees, rupeesToPaise } from "@/lib/format";
import { useCanWrite } from "@/store/session";
import { Field } from "@/components/ui/Field";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { PriceBreakdown } from "@/components/ui/PriceBreakdown";
import { ProblemCard } from "@/components/ui/ProblemCard";

interface FamilyRuleRow {
  family_id: string;
  /** Rupees as typed; blank means "no minimum". */
  minimum: string;
  /** Rupees as typed; blank means "no setup fee". */
  setup: string;
  /** Not edited here (needs the cart's discount line); carried through so a republish never drops them. */
  qty_breaks?: FamilyRule["qty_breaks"];
}

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
  hardware_markup_pct: string;
  family_rules: FamilyRuleRow[];
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

/** Policies published before the carriers work lack the new fields: treat them as 0 / no rules. */
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
    hardware_markup_pct: String(p.hardware_markup_pct ?? 0),
    family_rules: Object.entries(p.family_rules ?? {}).map(([family_id, r]) => ({
      family_id,
      minimum: r.minimum_subtotal_paise ? paiseToRupees(r.minimum_subtotal_paise) : "",
      setup: r.setup_fee_paise ? paiseToRupees(r.setup_fee_paise) : "",
      ...(r.qty_breaks?.length ? { qty_breaks: r.qty_breaks } : {}),
    })),
  };
}

/** Empty values are omitted: no markup field when 0, no rule for an Avatar without a minimum or setup fee. */
function toPolicy(d: Draft): PricingPolicy {
  const markup = Number.parseFloat(d.hardware_markup_pct);
  const rules: NonNullable<PricingPolicy["family_rules"]> = {};
  for (const row of d.family_rules) {
    const id = row.family_id.trim();
    if (!id) continue;
    const rule: FamilyRule = {};
    const minimum = rupeesToPaise(row.minimum);
    const setup = rupeesToPaise(row.setup);
    if (minimum > 0) rule.minimum_subtotal_paise = minimum;
    if (setup > 0) rule.setup_fee_paise = setup;
    if (row.qty_breaks?.length) rule.qty_breaks = row.qty_breaks;
    if (Object.keys(rule).length > 0) rules[id] = rule;
  }
  return {
    machine_rate_paise_per_hour: rupeesToPaise(d.machine_rate),
    finishing_fee_paise: Object.fromEntries(d.finishing.filter((f) => f.cls.trim()).map((f) => [f.cls.trim(), rupeesToPaise(f.fee)])),
    packaging_fee_paise: rupeesToPaise(d.packaging_fee),
    margin_pct: Number.parseFloat(d.margin_pct) || 0,
    round_to_rupees_ending_in: Math.min(9, Math.max(0, Number.parseInt(d.ending, 10) || 0)),
    shipping_flat_paise: rupeesToPaise(d.shipping_flat),
    free_shipping_above_paise: rupeesToPaise(d.free_above),
    shipping_label: d.shipping_label,
    ...(Number.isFinite(markup) && markup > 0 ? { hardware_markup_pct: markup } : {}),
    ...(Object.keys(rules).length > 0 ? { family_rules: rules } : {}),
  };
}

export interface PublishPolicyFormProps {
  active: PricingPolicyVersion;
  versions: PricingPolicyVersion[];
  materials: AdminMaterial[];
  /** Outcome families for the per-Avatar rules and the preview; may be empty while loading. */
  families: AdminFamily[];
  onPublished(created: PricingPolicyVersion): void;
}

/** Prefilled from the active policy; a live preview prices a sample piece under the draft. Owner-only publish. */
export function PublishPolicyForm({ active, versions, materials, families, onPublished }: PublishPolicyFormProps) {
  const canWrite = useCanWrite();
  const versionIds = useMemo(() => versions.map((v) => v.version), [versions]);
  const orderedFamilies = useMemo(() => sortFamilies(families), [families]);
  const [draft, setDraft] = useState<Draft>(() => fromPolicy(active.policy, versionIds));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const [published, setPublished] = useState<string>();

  // Sample piece for the preview: the Jharokha stand from the Checkout board.
  const [material, setMaterial] = useState(() => materials.find((m) => m.id === "terracotta_silk")?.id ?? materials[0]?.id ?? "");
  const [volume, setVolume] = useState("67.7");
  const [seconds, setSeconds] = useState("13200");
  const [previewFamily, setPreviewFamily] = useState("");
  const [preview, setPreview] = useState<Breakdown>();
  const [previewProblem, setPreviewProblem] = useState<Problem>();
  const [previewing, setPreviewing] = useState(false);

  const policy = useMemo(() => toPolicy(draft), [draft]);
  const previewKey = JSON.stringify({ policy, material, volume, seconds, previewFamily });

  useEffect(() => {
    const vol = Number.parseFloat(volume);
    const secs = Number.parseInt(seconds, 10);
    if (!material || !Number.isFinite(vol) || vol <= 0 || !Number.isFinite(secs) || secs <= 0) return;
    let cancelled = false;
    const t = setTimeout(async () => {
      setPreviewing(true);
      try {
        const res = await api.pricing.preview({ policy, material, extruded_volume_cm3: vol, print_seconds: secs, ...(previewFamily ? { family_id: previewFamily } : {}) });
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
    // previewKey encodes policy, material, volume, seconds and the preview family.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [previewKey]);

  function set<K extends keyof Draft>(k: K, v: Draft[K]) {
    setDraft((d) => ({ ...d, [k]: v }));
  }
  function setRule(i: number, patch: Partial<FamilyRuleRow>) {
    set(
      "family_rules",
      draft.family_rules.map((r, j) => (j === i ? { ...r, ...patch } : r)),
    );
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canWrite || blocked) return;
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
  const ruleIds = draft.family_rules.map((r) => r.family_id.trim()).filter(Boolean);
  const duplicateRule = ruleIds.find((id, i) => ruleIds.indexOf(id) !== i);
  const blocked = !versionOk || versionTaken || Boolean(duplicateRule);
  const dis = busy || !canWrite;
  const familyLabel = (id: string) => {
    const f = orderedFamilies.find((x) => x.id === id);
    return f ? `${familyTitle(f)} · ${f.id}` : id;
  };

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
            <Field label="Hardware markup (%)" hint="0–500, applied to bought-in part costs on the hardware line; 0 means cost price">
              {(id) => <input id={id} type="number" min="0" max="500" step="0.5" className="ak-input ak-input-sm" value={draft.hardware_markup_pct} onChange={(e) => set("hardware_markup_pct", e.target.value)} disabled={dis} />}
            </Field>
            <Field label="Shipping label" hint="Shown on the price breakdown (up to 60 characters)">
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

          <fieldset className="grid gap-2">
            <legend className="ak-label mb-1.5">Per-Avatar rules</legend>
            <p className="text-[11px] text-surface-muted">A minimum lifts the rounded subtotal of any piece in that family (a ₹49 keychain still costs the minimum); a setup fee adds a studio line, e.g. repair and orientation labour for Swaroop prints. Blank means none.</p>
            {draft.family_rules.length > 0 && (
              <div className="grid grid-cols-[minmax(0,1.6fr)_1fr_1fr_auto] gap-2 text-[11px] text-surface-muted" aria-hidden="true">
                <span>Avatar</span>
                <span>Minimum subtotal (₹)</span>
                <span>Setup fee (₹)</span>
                <span />
              </div>
            )}
            {draft.family_rules.map((r, i) => {
              const known = orderedFamilies.some((f) => f.id === r.family_id);
              return (
                <div key={i} className="grid grid-cols-[minmax(0,1.6fr)_1fr_1fr_auto] gap-2">
                  {orderedFamilies.length > 0 ? (
                    <select aria-label={`Avatar for rule ${i + 1}`} className="ak-input ak-input-sm" value={r.family_id} onChange={(e) => setRule(i, { family_id: e.target.value })} disabled={dis} required>
                      <option value="">Pick an Avatar…</option>
                      {r.family_id && !known && <option value={r.family_id}>{r.family_id} (unknown)</option>}
                      {orderedFamilies.map((f) => (
                        <option key={f.id} value={f.id}>
                          {familyTitle(f)} · {f.id}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input aria-label={`Family id for rule ${i + 1}`} className="ak-input ak-input-sm font-mono" pattern="^[a-z][a-z0-9_]*$" value={r.family_id} onChange={(e) => setRule(i, { family_id: e.target.value })} disabled={dis} placeholder="keychain" required />
                  )}
                  <input aria-label={`Minimum subtotal in rupees for ${familyLabel(r.family_id) || `rule ${i + 1}`}`} type="number" min="0" step="1" className="ak-input ak-input-sm" value={r.minimum} onChange={(e) => setRule(i, { minimum: e.target.value })} disabled={dis} placeholder="0" />
                  <input aria-label={`Setup fee in rupees for ${familyLabel(r.family_id) || `rule ${i + 1}`}`} type="number" min="0" step="1" className="ak-input ak-input-sm" value={r.setup} onChange={(e) => setRule(i, { setup: e.target.value })} disabled={dis} placeholder="0" />
                  <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => set("family_rules", draft.family_rules.filter((_, j) => j !== i))} disabled={dis} aria-label={`Remove rule for ${r.family_id || "this Avatar"}`}>
                    Remove
                  </button>
                  {r.qty_breaks?.length ? <span className="col-span-4 -mt-1 text-[11px] text-surface-muted">Keeps {r.qty_breaks.length} quantity break{r.qty_breaks.length === 1 ? "" : "s"} from the active policy (edited in a later release).</span> : null}
                </div>
              );
            })}
            {duplicateRule && <span className="text-xs text-danger">{familyLabel(duplicateRule)} appears twice; keep one row per Avatar.</span>}
            <button
              type="button"
              className="ak-btn ak-btn-secondary ak-btn-sm justify-self-start"
              onClick={() => set("family_rules", [...draft.family_rules, { family_id: orderedFamilies.find((f) => !ruleIds.includes(f.id))?.id ?? "", minimum: "", setup: "" }])}
              disabled={dis}
            >
              Add Avatar rule
            </button>
          </fieldset>

          {problem && <ProblemCard compact problem={problem} title={problem.code === "policy_version_exists" ? "Version id already used" : problem.code === "forbidden" ? "Owner only" : undefined} />}
          {published && (
            <p className="ak-well p-3 text-sm" role="status">
              Published <span className="font-mono font-semibold">{published}</span>; it is the active policy now.
            </p>
          )}
          <div className="flex flex-wrap items-center gap-3">
            <button type="submit" className="ak-btn ak-btn-primary" disabled={dis || blocked} title={canWrite ? undefined : "Owner only"}>
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
            <p className="text-[11px] text-surface-muted">Sample piece priced live by POST /pricing/preview as you type. Defaults are the Jharokha stand from the Checkout board; pick an Avatar to add its hardware, setup fee and minimum.</p>
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
          <Field label="Avatar" hint={previewFamily ? undefined : "None: a plain piece without hardware or family rules"}>
            {(id) => (
              <select id={id} className="ak-input ak-input-sm" value={previewFamily} onChange={(e) => setPreviewFamily(e.target.value)}>
                <option value="">No Avatar</option>
                {orderedFamilies.map((f) => (
                  <option key={f.id} value={f.id}>
                    {familyTitle(f)}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <div className="grid grid-cols-2 gap-2">
            <Field label="Extruded volume (cm³)">{(id) => <input id={id} type="number" min="0.1" step="0.1" className="ak-input ak-input-sm" value={volume} onChange={(e) => setVolume(e.target.value)} />}</Field>
            <Field label="Print time (s)">{(id) => <input id={id} type="number" min="1" step="60" className="ak-input ak-input-sm" value={seconds} onChange={(e) => setSeconds(e.target.value)} />}</Field>
          </div>
          <PriceBreakdown price={preview} loading={previewing} error={previewProblem?.detail ?? previewProblem?.title} footnote={preview ? `${preview.mass_g} g · policy: this draft${previewFamily ? ` · ${previewFamily}` : ""}` : undefined} />
          {previewProblem && <ProblemCard compact problem={previewProblem} />}
        </aside>
      </div>
    </section>
  );
}
