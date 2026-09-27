"use client";

import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { AdminFamily, AdminFamilyInput, AdminHardware, AdminMaterial, AdminTemplate, FamilyKind, FamilyTier, FeatureType, FinishClass, Problem, ShapeTolerance, Shelf } from "@/lib/api/types";
import { ENVIRONMENTS, environmentLabel } from "@/lib/catalog";
import { FAMILY_KINDS, FAMILY_TIERS, FEATURE_TYPES, SHAPE_TOLERANCES, familyTitle } from "@/lib/families";
import { useCanWrite } from "@/store/session";
import { Drawer } from "@/components/ui/Drawer";
import { Field } from "@/components/ui/Field";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Switch } from "@/components/ui/Switch";

/** Form state: numbers that may be blank are kept as strings and parsed on submit. */
interface Draft {
  id: string;
  codename: string;
  name: string;
  tagline: string;
  description: string;
  kind: FamilyKind;
  tier: FamilyTier;
  shelf: string;
  demand_rank: string;
  default_template_id: string;
  environment: string;
  envelope_min: string;
  envelope_max: string;
  hardware: { sku: string; qty: string }[];
  heat_safe_only: boolean;
  allowed_mode: "any" | "list";
  allowed: string[];
  excluded_finish_classes: FinishClass[];
  shape_tolerance: ShapeTolerance;
  accepts: FeatureType[];
  anchors: string;
  hero_volume: boolean;
  max_text_chars: string;
  available: boolean;
  sort_order: string;
}

const FINISH_CLASSES: FinishClass[] = ["matte", "silk"];

function fromFamily(f: AdminFamily | null, shelves: Shelf[]): Draft {
  if (!f) {
    return {
      id: "",
      codename: "",
      name: "",
      tagline: "",
      description: "",
      kind: "carrier",
      tier: "next",
      shelf: shelves[0]?.id ?? "",
      demand_rank: "",
      default_template_id: "",
      environment: "studio",
      envelope_min: "",
      envelope_max: "",
      hardware: [],
      heat_safe_only: false,
      allowed_mode: "any",
      allowed: [],
      excluded_finish_classes: [],
      shape_tolerance: "any",
      accepts: ["relief_image", "emboss_text", "motif"],
      anchors: "face",
      hero_volume: false,
      max_text_chars: "16",
      available: false,
      sort_order: "100",
    };
  }
  return {
    id: f.id,
    codename: f.codename,
    name: f.name,
    tagline: f.tagline ?? "",
    description: f.description ?? "",
    kind: f.kind,
    tier: f.tier,
    shelf: f.shelf,
    demand_rank: f.demand_rank === undefined ? "" : String(f.demand_rank),
    default_template_id: f.default_template_id,
    environment: f.environment ?? "studio",
    envelope_min: f.size_envelope_mm ? String(f.size_envelope_mm.min_longest_mm) : "",
    envelope_max: f.size_envelope_mm ? String(f.size_envelope_mm.max_longest_mm) : "",
    hardware: (f.hardware ?? []).map((h) => ({ sku: h.sku, qty: String(h.qty) })),
    heat_safe_only: f.material_rules?.heat_safe_only ?? false,
    allowed_mode: f.material_rules?.allowed ? "list" : "any",
    allowed: f.material_rules?.allowed ?? [],
    excluded_finish_classes: f.material_rules?.excluded_finish_classes ?? [],
    shape_tolerance: f.shape_tolerance,
    accepts: [...f.content_slot.accepts],
    anchors: (f.content_slot.anchors ?? []).join(", "),
    hero_volume: f.content_slot.hero_volume ?? false,
    max_text_chars: f.content_slot.max_text_chars === undefined ? "" : String(f.content_slot.max_text_chars),
    available: f.available,
    sort_order: String(f.sort_order),
  };
}

const num = (s: string): number | undefined => {
  const n = Number.parseFloat(s);
  return Number.isFinite(n) ? n : undefined;
};
const int = (s: string): number | undefined => {
  const n = Number.parseInt(s, 10);
  return Number.isFinite(n) ? n : undefined;
};

/** Draft → request body. Optional fields are omitted when blank so the API's defaults apply. */
function toInput(d: Draft): AdminFamilyInput {
  const min = num(d.envelope_min);
  const max = num(d.envelope_max);
  const demand = int(d.demand_rank);
  const maxChars = int(d.max_text_chars);
  const body: AdminFamilyInput = {
    id: d.id.trim(),
    codename: d.codename.trim(),
    name: d.name.trim(),
    kind: d.kind,
    tier: d.tier,
    shelf: d.shelf,
    default_template_id: d.default_template_id.trim(),
    environment: d.environment,
    hardware: d.hardware.filter((h) => h.sku).map((h) => ({ sku: h.sku, qty: Math.max(1, int(h.qty) ?? 1) })),
    material_rules: { heat_safe_only: d.heat_safe_only, allowed: d.allowed_mode === "list" ? [...d.allowed] : null, excluded_finish_classes: [...d.excluded_finish_classes] },
    shape_tolerance: d.shape_tolerance,
    content_slot: {
      accepts: [...d.accepts],
      anchors: d.anchors
        .split(",")
        .map((a) => a.trim())
        .filter(Boolean),
      hero_volume: d.hero_volume,
      ...(maxChars !== undefined ? { max_text_chars: maxChars } : {}),
    },
    available: d.available,
    sort_order: int(d.sort_order) ?? 100,
  };
  if (d.tagline.trim()) body.tagline = d.tagline.trim();
  if (d.description.trim()) body.description = d.description.trim();
  if (demand !== undefined) body.demand_rank = demand;
  if (min !== undefined && max !== undefined) body.size_envelope_mm = { min_longest_mm: min, max_longest_mm: max };
  return body;
}

const PROBLEM_TITLES: Record<string, string> = {
  family_exists: "That id is taken",
  unknown_family: "This Avatar no longer exists on the API",
  validation_failed: "The API rejected a field",
  forbidden: "Owner only",
};

export interface AvatarDrawerProps {
  /** Existing family to edit, `null` for "New Avatar", `undefined` when closed. */
  family: AdminFamily | null | undefined;
  shelves: Shelf[];
  hardware: AdminHardware[];
  materials: AdminMaterial[];
  templates: AdminTemplate[];
  onClose(): void;
  onSaved(family: AdminFamily): void;
}

export function AvatarDrawer({ family, shelves, hardware, materials, templates, onClose, onSaved }: AvatarDrawerProps) {
  const open = family !== undefined;
  return (
    <Drawer open={open} onClose={onClose} title={family ? familyTitle(family) : "New Avatar"} eyebrow={family ? `Avatar · ${family.id}` : "New outcome family"}>
      {open && <AvatarForm key={family?.id ?? "new"} family={family} shelves={shelves} hardware={hardware} materials={materials} templates={templates} onClose={onClose} onSaved={onSaved} />}
    </Drawer>
  );
}

function AvatarForm({ family, shelves, hardware, materials, templates, onClose, onSaved }: { family: AdminFamily | null } & Omit<AvatarDrawerProps, "family">) {
  const canWrite = useCanWrite();
  const editing = family !== null;
  const [draft, setDraft] = useState<Draft>(() => fromFamily(family, shelves));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const dis = busy || !canWrite;

  function set<K extends keyof Draft>(k: K, v: Draft[K]) {
    setDraft((d) => ({ ...d, [k]: v }));
  }
  function toggleIn<T extends string>(list: T[], value: T, on: boolean, order: readonly T[]): T[] {
    const next = on ? [...new Set([...list, value])] : list.filter((x) => x !== value);
    return [...next].sort((a, b) => order.indexOf(a) - order.indexOf(b));
  }

  // Client-side checks that mirror the schema's structural rules; the API still has the last word.
  const min = num(draft.envelope_min);
  const max = num(draft.envelope_max);
  const envelopeError =
    (draft.envelope_min.trim() === "") !== (draft.envelope_max.trim() === "")
      ? "Fill both ends of the envelope or leave both empty"
      : min !== undefined && max !== undefined && (min <= 0 || max <= 0)
        ? "Sizes must be above 0 mm"
        : min !== undefined && max !== undefined && min > max
          ? "Min must not exceed max"
          : undefined;
  const hardwareError = draft.hardware.some((h) => !h.sku) ? "Every hardware row needs a part" : draft.hardware.some((h) => (int(h.qty) ?? 0) < 1) ? "Quantity must be at least 1" : undefined;
  const maxChars = int(draft.max_text_chars);
  const maxCharsError = draft.max_text_chars.trim() !== "" && (maxChars === undefined || maxChars < 1 || maxChars > 40) ? "1–40 characters" : undefined;
  const allowedError = draft.allowed_mode === "list" && draft.allowed.length === 0 ? "Pick at least one material, or allow any" : undefined;
  const blocked = Boolean(envelopeError || hardwareError || maxCharsError || allowedError);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canWrite || blocked) return;
    setBusy(true);
    setProblem(undefined);
    const body = toInput(draft);
    try {
      const saved = editing ? await api.families.update(family.id, body) : await api.families.create(body);
      onSaved(saved);
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(false);
    }
  }

  const templateKnown = templates.some((t) => t.id === draft.default_template_id);
  const acceptsText = draft.accepts.includes("emboss_text");
  const acceptsHero = draft.accepts.includes("hero_mesh");

  return (
    <form onSubmit={submit} className="grid gap-6" aria-busy={busy}>
      <OwnerOnlyHint what="Avatar changes" />

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2">Naming and copy</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Id" hint="snake_case; equals `family` in specs and descriptors; fixed once created">
            {(id) => <input id={id} className="ak-input ak-input-sm font-mono" pattern="^[a-z][a-z0-9_]*$" maxLength={40} value={draft.id} onChange={(e) => set("id", e.target.value)} disabled={dis || editing} required />}
          </Field>
          <Field label="Codename" hint="Brand name customers see, e.g. Saathi (up to 40)">
            {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={40} value={draft.codename} onChange={(e) => set("codename", e.target.value)} disabled={dis} required />}
          </Field>
          <Field label="Name" hint="Plain descriptor always shown with the codename, e.g. Keychain & bag charm" className="sm:col-span-2">
            {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={80} value={draft.name} onChange={(e) => set("name", e.target.value)} disabled={dis} required />}
          </Field>
          <Field label="Tagline" hint="Up to 120 characters" className="sm:col-span-2">
            {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={120} value={draft.tagline} onChange={(e) => set("tagline", e.target.value)} disabled={dis} />}
          </Field>
          <Field label="Description" hint="Craft register, never mesh or STL (up to 500)" className="sm:col-span-2">
            {(id) => <textarea id={id} className="ak-input" rows={3} maxLength={500} value={draft.description} onChange={(e) => set("description", e.target.value)} disabled={dis} />}
          </Field>
        </div>
      </fieldset>

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2">Classification</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Kind" hint={FAMILY_KINDS.find((k) => k.id === draft.kind)?.hint}>
            {(id) => (
              <select id={id} className="ak-input ak-input-sm" value={draft.kind} onChange={(e) => set("kind", e.target.value as FamilyKind)} disabled={dis}>
                {FAMILY_KINDS.map((k) => (
                  <option key={k.id} value={k.id}>
                    {k.label}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Tier" hint="Roadmap slot from the research; Available is the staff switch">
            {(id) => (
              <select id={id} className="ak-input ak-input-sm" value={draft.tier} onChange={(e) => set("tier", e.target.value as FamilyTier)} disabled={dis}>
                {FAMILY_TIERS.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.label}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Shelf" hint="Shop shelf this Avatar sits on">
            {(id) => (
              <select id={id} className="ak-input ak-input-sm" value={draft.shelf} onChange={(e) => set("shelf", e.target.value)} disabled={dis} required>
                {shelves.length === 0 && <option value="">Loading shelves…</option>}
                {draft.shelf && !shelves.some((s) => s.id === draft.shelf) && <option value={draft.shelf}>{draft.shelf} (unknown shelf)</option>}
                {shelves.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.label}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Demand rank" hint="From the market research; 1 is the most asked-for">
            {(id) => <input id={id} type="number" min="1" step="1" className="ak-input ak-input-sm" value={draft.demand_rank} onChange={(e) => set("demand_rank", e.target.value)} disabled={dis} />}
          </Field>
          <Field label="Default template id" hint={draft.default_template_id && !templateKnown ? "Not in the geometry service yet: the Avatar stays 'no template' until it exists" : "Used when a request names only the family"}>
            {(id) => (
              <>
                <input id={id} className="ak-input ak-input-sm font-mono" list="avatar-template-ids" pattern="^[a-z][a-z0-9_]*$" value={draft.default_template_id} onChange={(e) => set("default_template_id", e.target.value)} disabled={dis} required />
                <datalist id="avatar-template-ids">
                  {templates.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </datalist>
              </>
            )}
          </Field>
          <Field label="Environment" hint="Default viewer backdrop before a template is chosen">
            {(id) => (
              <select id={id} className="ak-input ak-input-sm" value={draft.environment} onChange={(e) => set("environment", e.target.value)} disabled={dis}>
                {ENVIRONMENTS.map((env) => (
                  <option key={env} value={env}>
                    {environmentLabel(env)}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Shape tolerance" hint={SHAPE_TOLERANCES.find((s) => s.id === draft.shape_tolerance)?.hint}>
            {(id) => (
              <select id={id} className="ak-input ak-input-sm" value={draft.shape_tolerance} onChange={(e) => set("shape_tolerance", e.target.value as ShapeTolerance)} disabled={dis}>
                {SHAPE_TOLERANCES.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.label}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Sort order" hint="Lower comes first in the Create picker">
            {(id) => <input id={id} type="number" step="1" className="ak-input ak-input-sm" value={draft.sort_order} onChange={(e) => set("sort_order", e.target.value)} disabled={dis} required />}
          </Field>
          <Field label="Available" hint="Off hides the Avatar from the Create picker; designs already made keep working" inline className="sm:col-span-2">
            {(id) => <Switch id={id} label="Available" checked={draft.available} onChange={(v) => set("available", v)} disabled={dis} />}
          </Field>
        </div>
      </fieldset>

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2">Size envelope (longest side, mm)</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Min longest (mm)" error={envelopeError}>
            {(id) => <input id={id} type="number" min="0" step="1" className="ak-input ak-input-sm" value={draft.envelope_min} onChange={(e) => set("envelope_min", e.target.value)} disabled={dis} />}
          </Field>
          <Field label="Max longest (mm)" hint="Drives copy like 'Palm-sized · 40–70 mm' and validates raw sizes">
            {(id) => <input id={id} type="number" min="0" step="1" className="ak-input ak-input-sm" value={draft.envelope_max} onChange={(e) => set("envelope_max", e.target.value)} disabled={dis} />}
          </Field>
        </div>
      </fieldset>

      <fieldset className="grid gap-2">
        <legend className="ak-label mb-1.5">Hardware (default bill of materials)</legend>
        <p className="text-[11px] text-surface-muted">Bought-in parts packed with every piece; a template descriptor&apos;s own hardware list overrides this.</p>
        {draft.hardware.map((h, i) => (
          <div key={i} className="grid grid-cols-[minmax(0,1fr)_84px_auto] gap-2">
            <select aria-label={`Hardware part ${i + 1}`} className="ak-input ak-input-sm" value={h.sku} onChange={(e) => set("hardware", draft.hardware.map((x, j) => (j === i ? { ...x, sku: e.target.value } : x)))} disabled={dis} required>
              <option value="">Pick a part…</option>
              {h.sku && !hardware.some((x) => x.sku === h.sku) && <option value={h.sku}>{h.sku} (unknown sku)</option>}
              {hardware.map((x) => (
                <option key={x.sku} value={x.sku}>
                  {x.name} · {x.sku}
                  {x.available ? "" : " (unavailable)"}
                </option>
              ))}
            </select>
            <input aria-label={`Quantity of part ${i + 1}`} type="number" min="1" step="1" className="ak-input ak-input-sm" value={h.qty} onChange={(e) => set("hardware", draft.hardware.map((x, j) => (j === i ? { ...x, qty: e.target.value } : x)))} disabled={dis} required />
            <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => set("hardware", draft.hardware.filter((_, j) => j !== i))} disabled={dis} aria-label={`Remove ${h.sku || "hardware row"}`}>
              Remove
            </button>
          </div>
        ))}
        {hardwareError && <span className="text-xs text-danger">{hardwareError}</span>}
        <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm justify-self-start" onClick={() => set("hardware", [...draft.hardware, { sku: hardware[0]?.sku ?? "", qty: "1" }])} disabled={dis || hardware.length === 0} title={hardware.length === 0 ? "No hardware items yet: add some on the Hardware page" : undefined}>
          Add part
        </button>
      </fieldset>

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2">Material rules</legend>
        <Field label="Heat-safe materials only" hint="Coasters, diya holders, car pieces" inline>
          {(id) => <Switch id={id} label="Heat-safe materials only" checked={draft.heat_safe_only} onChange={(v) => set("heat_safe_only", v)} disabled={dis} />}
        </Field>
        <div className="grid gap-2" role="radiogroup" aria-labelledby="allowed-materials-label">
          <span id="allowed-materials-label" className="text-sm font-medium">
            Allowed materials
          </span>
          <label className="flex items-center gap-2 text-sm">
            <input type="radio" name="allowed-mode" checked={draft.allowed_mode === "any"} onChange={() => set("allowed_mode", "any")} disabled={dis} />
            Any available material
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input type="radio" name="allowed-mode" checked={draft.allowed_mode === "list"} onChange={() => set("allowed_mode", "list")} disabled={dis} />
            Only these
          </label>
          {draft.allowed_mode === "list" && (
            <div className="ml-6 grid gap-1.5 sm:grid-cols-2">
              {materials.length === 0 && <span className="text-xs text-surface-muted">Materials are still loading.</span>}
              {materials.map((m) => (
                <label key={m.id} className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={draft.allowed.includes(m.id)} onChange={(e) => set("allowed", toggleIn(draft.allowed, m.id, e.target.checked, materials.map((x) => x.id)))} disabled={dis} />
                  <span>
                    {m.name} <span className="font-mono text-[11px] text-surface-muted">{m.id}</span>
                  </span>
                </label>
              ))}
              {draft.allowed
                .filter((id) => !materials.some((m) => m.id === id))
                .map((id) => (
                  <label key={id} className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked onChange={() => set("allowed", draft.allowed.filter((x) => x !== id))} disabled={dis} />
                    <span className="font-mono text-[12px]">{id}</span> <span className="text-[11px] text-surface-muted">(unknown material)</span>
                  </label>
                ))}
              {allowedError && <span className="text-xs text-danger sm:col-span-2">{allowedError}</span>}
            </div>
          )}
        </div>
        <div className="grid gap-1.5">
          <span className="text-sm font-medium">Excluded finish classes</span>
          <div className="flex flex-wrap gap-4">
            {FINISH_CLASSES.map((cls) => (
              <label key={cls} className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={draft.excluded_finish_classes.includes(cls)} onChange={(e) => set("excluded_finish_classes", toggleIn(draft.excluded_finish_classes, cls, e.target.checked, FINISH_CLASSES))} disabled={dis} />
                {cls}
              </label>
            ))}
          </div>
        </div>
      </fieldset>

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2">Content slot (Chhaap)</legend>
        <div className="grid gap-1.5">
          <span className="text-sm font-medium">Accepts</span>
          <p className="text-[11px] text-surface-muted">What personal content this Avatar carries. A template&apos;s features_supported must stay within this list.</p>
          <div className="grid gap-1.5 sm:grid-cols-2">
            {FEATURE_TYPES.map((f) => (
              <label key={f.id} className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={draft.accepts.includes(f.id)} onChange={(e) => set("accepts", toggleIn(draft.accepts, f.id, e.target.checked, FEATURE_TYPES.map((x) => x.id)))} disabled={dis} />
                <span>
                  <strong className="font-semibold">{f.codename}</strong> · {f.label} <span className="font-mono text-[11px] text-surface-muted">{f.id}</span>
                </span>
              </label>
            ))}
          </div>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Anchors" hint="Anchor ids the consumer UI offers first, comma separated (face, back)" className="sm:col-span-2">
            {(id) => <input id={id} className="ak-input ak-input-sm font-mono" value={draft.anchors} onChange={(e) => set("anchors", e.target.value)} disabled={dis} />}
          </Field>
          <Field label="Max text characters" hint={acceptsText ? "1–40; applies to Naam (emboss_text)" : "Only used when Naam (emboss_text) is accepted"} error={maxCharsError}>
            {(id) => <input id={id} type="number" min="1" max="40" step="1" className="ak-input ak-input-sm" value={draft.max_text_chars} onChange={(e) => set("max_text_chars", e.target.value)} disabled={dis} />}
          </Field>
          <Field label="Hero volume" hint={acceptsHero && !draft.hero_volume ? "Roop (hero_mesh) is accepted: usually on" : "On when the Avatar carries a customer's own 3D form in a volume anchor"} inline>
            {(id) => <Switch id={id} label="Hero volume" checked={draft.hero_volume} onChange={(v) => set("hero_volume", v)} disabled={dis} />}
          </Field>
        </div>
      </fieldset>

      {problem && <ProblemCard compact problem={problem} title={PROBLEM_TITLES[problem.code ?? ""]} />}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="ak-btn ak-btn-primary" disabled={dis || blocked} title={canWrite ? undefined : "Owner only"}>
          {busy ? "Saving…" : editing ? "Save Avatar" : "Create Avatar"}
        </button>
        <button type="button" className="ak-btn ak-btn-secondary" onClick={onClose} disabled={busy}>
          {canWrite ? "Cancel" : "Close"}
        </button>
      </div>
    </form>
  );
}
