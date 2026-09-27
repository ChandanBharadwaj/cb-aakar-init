"use client";

import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { AdminMaterial, AdminMaterialInput, Problem } from "@/lib/api/types";
import { paiseToRupees, rupeesToPaise } from "@/lib/format";
import { useCanWrite } from "@/store/session";
import { Swatch } from "./Swatch";
import { Drawer } from "@/components/ui/Drawer";
import { Field } from "@/components/ui/Field";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Switch } from "@/components/ui/Switch";

const BLANK: AdminMaterialInput = {
  id: "",
  name: "",
  filament: "",
  density_g_cm3: 1.24,
  finish_class: "matte",
  rate_per_g_paise: 40000 / 100,
  heat_safe: false,
  available: true,
  sort_order: 100,
  pbr: { color: "#F4F1EA", roughness: 0.6, metalness: 0, clearcoat: 0, clearcoat_roughness: 0, sheen: 0, sheen_color: "#FFFFFF" },
};

const PBR_NUMBERS: { key: "roughness" | "metalness" | "clearcoat" | "clearcoat_roughness" | "sheen"; label: string }[] = [
  { key: "roughness", label: "Roughness" },
  { key: "metalness", label: "Metalness" },
  { key: "clearcoat", label: "Clearcoat" },
  { key: "clearcoat_roughness", label: "Clearcoat roughness" },
  { key: "sheen", label: "Sheen" },
];

export interface MaterialDrawerProps {
  /** Existing material to edit, `null` for "Add material", `undefined` when closed. */
  material: AdminMaterial | null | undefined;
  onClose(): void;
  onSaved(material: AdminMaterial): void;
}

export function MaterialDrawer({ material, onClose, onSaved }: MaterialDrawerProps) {
  const open = material !== undefined;
  return (
    <Drawer open={open} onClose={onClose} title={material ? material.name : "Add material"} eyebrow={material ? `Material · ${material.id}` : "New material"}>
      {open && <MaterialForm key={material?.id ?? "new"} material={material} onClose={onClose} onSaved={onSaved} />}
    </Drawer>
  );
}

function MaterialForm({ material, onClose, onSaved }: { material: AdminMaterial | null; onClose(): void; onSaved(m: AdminMaterial): void }) {
  const canWrite = useCanWrite();
  const editing = material !== null;
  const [draft, setDraft] = useState<AdminMaterialInput>(() => {
    if (!material) return BLANK;
    return {
      id: material.id,
      name: material.name,
      filament: material.filament,
      density_g_cm3: material.density_g_cm3,
      finish_class: material.finish_class,
      rate_per_g_paise: material.rate_per_g_paise,
      heat_safe: material.heat_safe,
      available: material.available,
      sort_order: material.sort_order,
      pbr: { ...material.pbr },
    };
  });
  const [rate, setRate] = useState(() => paiseToRupees(material?.rate_per_g_paise ?? BLANK.rate_per_g_paise));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const dis = busy || !canWrite;

  function set<K extends keyof AdminMaterialInput>(k: K, v: AdminMaterialInput[K]) {
    setDraft((d) => ({ ...d, [k]: v }));
  }
  function setPbr<K extends keyof AdminMaterialInput["pbr"]>(k: K, v: AdminMaterialInput["pbr"][K]) {
    setDraft((d) => ({ ...d, pbr: { ...d.pbr, [k]: v } }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canWrite) return;
    setBusy(true);
    setProblem(undefined);
    const body: AdminMaterialInput = { ...draft, id: draft.id.trim(), name: draft.name.trim(), filament: draft.filament.trim(), rate_per_g_paise: rupeesToPaise(rate) };
    try {
      const saved = editing ? await api.materials.update(material.id, body) : await api.materials.create(body);
      onSaved(saved);
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form id="material-form" onSubmit={submit} className="grid gap-5" aria-busy={busy}>
      <OwnerOnlyHint what="Material changes" />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Id" hint="snake_case, fixed once created">
          {(id) => <input id={id} className="ak-input ak-input-sm font-mono" pattern="^[a-z][a-z0-9_]*$" value={draft.id} onChange={(e) => set("id", e.target.value)} disabled={dis || editing} required />}
        </Field>
        <Field label="Name" hint="Up to 40 characters">
          {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={40} value={draft.name} onChange={(e) => set("name", e.target.value)} disabled={dis} required />}
        </Field>
        <Field label="Filament" hint="What the printer loads, up to 80 characters" className="sm:col-span-2">
          {(id) => <input id={id} className="ak-input ak-input-sm" maxLength={80} value={draft.filament} onChange={(e) => set("filament", e.target.value)} disabled={dis} required />}
        </Field>
        <Field label="Density (g/cm³)" hint="0.5–3">
          {(id) => <input id={id} type="number" step="0.01" min="0.5" max="3" className="ak-input ak-input-sm" value={draft.density_g_cm3} onChange={(e) => set("density_g_cm3", Number(e.target.value))} disabled={dis} required />}
        </Field>
        <Field label="Finish class">
          {(id) => (
            <select id={id} className="ak-input ak-input-sm" value={draft.finish_class} onChange={(e) => set("finish_class", e.target.value as AdminMaterialInput["finish_class"])} disabled={dis}>
              <option value="matte">matte</option>
              <option value="silk">silk</option>
            </select>
          )}
        </Field>
        <Field label="Rate (₹ per gram)" hint="Stored as integer paise">
          {(id) => <input id={id} type="number" step="0.01" min="0" className="ak-input ak-input-sm" value={rate} onChange={(e) => setRate(e.target.value)} disabled={dis} required />}
        </Field>
        <Field label="Sort order" hint="Lower comes first in the finish chips">
          {(id) => <input id={id} type="number" step="1" className="ak-input ak-input-sm" value={draft.sort_order} onChange={(e) => set("sort_order", Number(e.target.value))} disabled={dis} />}
        </Field>
        <Field label="Heat safe" hint="Coasters, kitchen pieces" inline>
          {(id) => <Switch id={id} label="Heat safe" checked={draft.heat_safe} onChange={(v) => set("heat_safe", v)} disabled={dis} />}
        </Field>
        <Field label="Available" hint="Off hides it from the storefront finish chips" inline>
          {(id) => <Switch id={id} label="Available" checked={draft.available} onChange={(v) => set("available", v)} disabled={dis} />}
        </Field>
      </div>

      <fieldset className="grid gap-4">
        <legend className="ak-label mb-2 flex items-center gap-2">
          Digital material (PBR preset) <Swatch pbr={draft.pbr} finishClass={draft.finish_class} />
        </legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Colour (sRGB hex)">
            {(id) => (
              <div className="flex gap-2">
                <input aria-label="Colour picker" type="color" className="h-[38px] w-12 flex-none rounded-control border border-surface-border bg-surface-card" value={/^#[0-9a-f]{6}$/i.test(draft.pbr.color) ? draft.pbr.color : "#000000"} onChange={(e) => setPbr("color", e.target.value.toUpperCase())} disabled={dis} />
                <input id={id} className="ak-input ak-input-sm font-mono" pattern="^#[0-9A-Fa-f]{6}$" value={draft.pbr.color} onChange={(e) => setPbr("color", e.target.value)} disabled={dis} required />
              </div>
            )}
          </Field>
          <Field label="Sheen colour (sRGB hex)">
            {(id) => (
              <div className="flex gap-2">
                <input aria-label="Sheen colour picker" type="color" className="h-[38px] w-12 flex-none rounded-control border border-surface-border bg-surface-card" value={/^#[0-9a-f]{6}$/i.test(draft.pbr.sheen_color ?? "") ? (draft.pbr.sheen_color as string) : "#FFFFFF"} onChange={(e) => setPbr("sheen_color", e.target.value.toUpperCase())} disabled={dis} />
                <input id={id} className="ak-input ak-input-sm font-mono" pattern="^#[0-9A-Fa-f]{6}$" value={draft.pbr.sheen_color ?? ""} onChange={(e) => setPbr("sheen_color", e.target.value)} disabled={dis} />
              </div>
            )}
          </Field>
          {PBR_NUMBERS.map((f) => (
            <Field key={f.key} label={f.label} hint="0–1">
              {(id) => <input id={id} type="number" step="0.01" min="0" max="1" className="ak-input ak-input-sm" value={draft.pbr[f.key] ?? 0} onChange={(e) => setPbr(f.key, Number(e.target.value))} disabled={dis} />}
            </Field>
          ))}
        </div>
      </fieldset>

      {problem && <ProblemCard compact problem={problem} title={problem.code === "material_exists" ? "That id is taken" : problem.code === "forbidden" ? "Owner only" : undefined} />}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="ak-btn ak-btn-primary" disabled={dis} title={canWrite ? undefined : "Owner only"}>
          {busy ? "Saving…" : editing ? "Save material" : "Add material"}
        </button>
        <button type="button" className="ak-btn ak-btn-secondary" onClick={onClose} disabled={busy}>
          {canWrite ? "Cancel" : "Close"}
        </button>
      </div>
    </form>
  );
}
