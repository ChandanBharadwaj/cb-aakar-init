"use client";

import { useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { AdminFamily, AdminMaterial, AdminTemplate, CatalogItem, CatalogItemInput, Environment, Problem, Shelf } from "@/lib/api/types";
import { shelfLabel, sortShelves } from "@/lib/catalog";
import { familyTitle, sortFamilies } from "@/lib/families";
import { paiseToRupees, rupeesToPaise } from "@/lib/format";
import { useCanWrite } from "@/store/session";
import { EnvironmentSelect } from "@/components/environments/Backdrops";
import { Drawer } from "@/components/ui/Drawer";
import { Field } from "@/components/ui/Field";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Switch } from "@/components/ui/Switch";

/** The input shape is the item minus nothing: CatalogItem carries exactly the CatalogItemInput fields. */
export function toInput(item: CatalogItem): CatalogItemInput {
  return {
    slug: item.slug,
    name: item.name,
    category: item.category,
    family_id: item.family_id ?? null,
    description: item.description,
    template_id: item.template_id,
    default_params: item.default_params,
    default_material: item.default_material,
    base_price_paise: item.base_price_paise,
    specs_line: item.specs_line,
    environment: item.environment,
    available: item.available,
    media: item.media,
  };
}

const BLANK: CatalogItemInput = { slug: "", name: "", category: "", family_id: null, description: "", template_id: "", default_params: {}, default_material: "", base_price_paise: 0, specs_line: "", environment: "studio", available: false, media: [] };

const PROBLEM_TITLES: Record<string, string> = {
  slug_exists: "That slug is taken",
  unknown_family: "Unknown Avatar",
  validation_failed: "The API rejected a field",
  forbidden: "Owner only",
};

export interface CatalogDrawerProps {
  item: CatalogItem | null | undefined;
  materials: AdminMaterial[];
  templates: AdminTemplate[];
  shelves: Shelf[];
  families: AdminFamily[];
  /** Backdrops from GET /admin/api/environments: the valid `environment` values. */
  environments: Environment[];
  onClose(): void;
  onSaved(item: CatalogItem): void;
}

export function CatalogDrawer({ item, materials, templates, shelves, families, environments, onClose, onSaved }: CatalogDrawerProps) {
  const open = item !== undefined;
  return (
    <Drawer open={open} onClose={onClose} title={item ? item.name : "Add item"} eyebrow={item ? `Catalog · ${item.slug}` : "New catalog item"}>
      {open && <CatalogForm key={item?.slug ?? "new"} item={item} materials={materials} templates={templates} shelves={shelves} families={families} environments={environments} onClose={onClose} onSaved={onSaved} />}
    </Drawer>
  );
}

function CatalogForm({ item, materials, templates, shelves, families, environments, onClose, onSaved }: { item: CatalogItem | null } & Omit<CatalogDrawerProps, "item">) {
  const canWrite = useCanWrite();
  const editing = item !== null;
  const orderedShelves = sortShelves(shelves);
  const orderedFamilies = sortFamilies(families);
  const [draft, setDraft] = useState<CatalogItemInput>(() => (item ? toInput(item) : { ...BLANK, category: orderedShelves[0]?.id ?? "", default_material: materials[0]?.id ?? "" }));
  const [price, setPrice] = useState(() => paiseToRupees(item?.base_price_paise ?? 0));
  const [paramsText, setParamsText] = useState(() => JSON.stringify(item?.default_params ?? {}, null, 2));
  const [mediaText, setMediaText] = useState(() => (item?.media ?? []).map((m) => `${m.kind ?? "image"} ${m.url ?? ""}`.trim()).join("\n"));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const dis = busy || !canWrite;

  let paramsError: string | undefined;
  let params: Record<string, unknown> = {};
  try {
    const parsed: unknown = JSON.parse(paramsText || "{}");
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) paramsError = "Must be a JSON object";
    else params = parsed as Record<string, unknown>;
  } catch {
    paramsError = "Not valid JSON";
  }

  function set<K extends keyof CatalogItemInput>(k: K, v: CatalogItemInput[K]) {
    setDraft((d) => ({ ...d, [k]: v }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canWrite || paramsError) return;
    setBusy(true);
    setProblem(undefined);
    const media = mediaText
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean)
      .map((l) => {
        const [kind, ...rest] = l.split(/\s+/);
        return rest.length ? { kind, url: rest.join(" ") } : { kind: "image", url: kind };
      });
    const body: CatalogItemInput = { ...draft, slug: draft.slug.trim(), name: draft.name.trim(), template_id: draft.template_id.trim(), family_id: draft.family_id || null, default_params: params, base_price_paise: rupeesToPaise(price), media };
    if (!body.description) delete body.description;
    try {
      const saved = editing ? await api.catalog.update(item.slug, body) : await api.catalog.create(body);
      onSaved(saved);
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(false);
    }
  }

  const templateKnown = templates.some((t) => t.id === draft.template_id);
  const shelfKnown = !draft.category || orderedShelves.some((s) => s.id === draft.category);
  const family = orderedFamilies.find((f) => f.id === draft.family_id);
  const familyHint = !draft.family_id
    ? "Optional: the outcome family (Avatar) this item belongs to"
    : !family
      ? "Not in the Avatars list; the API answers 422 unknown_family for ids it hasn't seeded"
      : family.default_template_id !== draft.template_id
        ? `${family.codename}'s default template is ${family.default_template_id}`
        : `${family.kind} · ${family.tier}${family.available ? "" : " · hidden from the picker"}`;

  return (
    <form onSubmit={submit} className="grid gap-5" aria-busy={busy}>
      <OwnerOnlyHint what="Catalog changes" />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Slug" hint="3–60 lowercase letters, digits, dashes; fixed once created">
          {(id) => <input id={id} className="ak-input ak-input-sm font-mono" pattern="^[a-z0-9-]{3,60}$" value={draft.slug} onChange={(e) => set("slug", e.target.value)} disabled={dis || editing} required />}
        </Field>
        <Field label="Name" hint="Up to 80 characters">
          {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={80} value={draft.name} onChange={(e) => set("name", e.target.value)} disabled={dis} required />}
        </Field>
        <Field label="Shelf" hint={shelfKnown ? "Shop shelf (catalog category)" : "This shelf is no longer served by the API; pick another before saving"}>
          {(id) => (
            <select id={id} className="ak-input ak-input-sm" value={draft.category} onChange={(e) => set("category", e.target.value)} disabled={dis} required>
              {orderedShelves.length === 0 && <option value="">Loading shelves…</option>}
              {!shelfKnown && <option value={draft.category}>{shelfLabel(undefined, draft.category)} (unknown shelf)</option>}
              {orderedShelves.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Avatar (family)" hint={familyHint}>
          {(id) => (
            <select id={id} className="ak-input ak-input-sm" value={draft.family_id ?? ""} onChange={(e) => set("family_id", e.target.value || null)} disabled={dis}>
              <option value="">No Avatar</option>
              {draft.family_id && !family && <option value={draft.family_id}>{draft.family_id} (unknown)</option>}
              {orderedFamilies.map((f) => (
                <option key={f.id} value={f.id}>
                  {familyTitle(f)} · {f.id}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Template id" hint={draft.template_id && !templateKnown ? "Not in the geometry service yet: the item stays 'coming soon' until the template exists" : "Known template"}>
          {(id) => (
            <>
              <input id={id} className="ak-input ak-input-sm font-mono" list="template-ids" value={draft.template_id} onChange={(e) => set("template_id", e.target.value)} disabled={dis} required />
              <datalist id="template-ids">
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </datalist>
            </>
          )}
        </Field>
        <Field label="Default material">
          {(id) => (
            <select id={id} className="ak-input ak-input-sm" value={draft.default_material} onChange={(e) => set("default_material", e.target.value)} disabled={dis} required>
              {materials.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Base price (₹)" hint={`Shown on the Shop card · ${formatPaise(rupeesToPaise(price))}`}>
          {(id) => <input id={id} type="number" min="0" step="1" className="ak-input ak-input-sm" value={price} onChange={(e) => setPrice(e.target.value)} disabled={dis} required />}
        </Field>
        <Field label="Environment" hint="Viewer backdrop (Backgrounds page)">
          {(id) => <EnvironmentSelect id={id} value={draft.environment ?? "studio"} onChange={(v) => set("environment", v)} environments={environments} disabled={dis} />}
        </Field>
        <Field label="Available" hint="Off shows 'Coming soon' in the Shop" inline>
          {(id) => <Switch id={id} label="Available" checked={draft.available} onChange={(v) => set("available", v)} disabled={dis} />}
        </Field>
        <Field label="Specs line" hint="Up to 120 characters, e.g. Fits phones to 6.9″ · 92 × 78 × 120 mm · 64 g" className="sm:col-span-2">
          {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={120} value={draft.specs_line} onChange={(e) => set("specs_line", e.target.value)} disabled={dis} required />}
        </Field>
        <Field label="Description" hint="Up to 500 characters" className="sm:col-span-2">
          {(id) => <textarea id={id} className="ak-input" rows={3} maxLength={500} value={draft.description ?? ""} onChange={(e) => set("description", e.target.value)} disabled={dis} />}
        </Field>
        <Field label="Default params (JSON)" hint="Template parameter values the Shop starts from" error={paramsError} className="sm:col-span-2">
          {(id) => <textarea id={id} className="ak-input font-mono text-[12.5px]" rows={5} value={paramsText} onChange={(e) => setParamsText(e.target.value)} disabled={dis} spellCheck={false} />}
        </Field>
        <Field label="Media" hint="One per line: kind URL (e.g. image https://…). Leave empty for the clay placeholder." className="sm:col-span-2">
          {(id) => <textarea id={id} className="ak-input font-mono text-[12.5px]" rows={3} value={mediaText} onChange={(e) => setMediaText(e.target.value)} disabled={dis} spellCheck={false} />}
        </Field>
      </div>
      {problem && <ProblemCard compact problem={problem} title={PROBLEM_TITLES[problem.code ?? ""]} />}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="ak-btn ak-btn-primary" disabled={dis || Boolean(paramsError) || !draft.category} title={canWrite ? undefined : "Owner only"}>
          {busy ? "Saving…" : editing ? "Save item" : "Add item"}
        </button>
        <button type="button" className="ak-btn ak-btn-secondary" onClick={onClose} disabled={busy}>
          {canWrite ? "Cancel" : "Close"}
        </button>
      </div>
    </form>
  );
}
