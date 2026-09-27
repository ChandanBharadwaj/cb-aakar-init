"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useMemo, useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { CatalogItem, Family, Material, ParamValues, Problem } from "@/lib/api/types";
import { presetStyle, STYLE_LABELS, type DuniyaPreset } from "@/lib/experiences";
import { allowedMaterialIds, defaultTemplate, envelopeLine, hardwareNames, hardwareSentence, packedHardware, priceFromLabel } from "@/lib/families";
import { CHHAAP, contentAnchors, familyLabel, featureSummary, featuresFitting, featuresForSubmit, templateTakes, type Feature } from "@/lib/features";
import { capitalise } from "@/lib/format";
import { REJECTED_COPY } from "@/lib/uploads";
import { FALLBACK_MATERIALS } from "@/lib/viewer/materials";
import { environmentLabel } from "@/lib/viewer/environments";
import { uploadHold, useDesignStore } from "@/store/design";
import { ContentSlotPanel } from "@/components/design/ContentSlotPanel";
import { DuniyaChip } from "@/components/duniya/DuniyaChip";
import { FinishChips } from "@/components/ui/FinishChips";
import { useMotifs } from "./useMotifs";

export interface ContentComposerProps {
  family: Family;
  /** From `GET /api/catalog/materials`; empty falls back to the token presets. */
  materials: Material[];
  /** A Shop item of this family ("Make it yours"): seeds the template, defaults, finish and title. */
  item?: CatalogItem;
  /** The customer's words from the prompt bar, used as the working title. */
  prompt?: string;
  /**
   * The Duniya experience from `?duniya=<slug>`: its chip in the header, its backdrop and (when the template offers it)
   * its style carried into the studio, its motif pack first in Buti, and `experience_id` on the design.
   */
  duniya?: DuniyaPreset;
}

/** Only primitive defaults travel as `params`; the contract allows number, boolean and string. */
function primitiveParams(values: Record<string, unknown> | undefined): ParamValues | undefined {
  if (!values) return undefined;
  const out: ParamValues = {};
  for (const [k, v] of Object.entries(values)) if (typeof v === "number" || typeof v === "boolean" || typeof v === "string") out[k] = v;
  return Object.keys(out).length > 0 ? out : undefined;
}

/**
 * The Avatar composer: pick a template when the family has several, fill the Chhaap, choose a finish,
 * then "Sculpt" → `POST /api/designs {source: "create", family_id, template_id, features, material, title,
 * experience_id?}` → the studio with the job, exactly like the Shop and Remix paths. Sculpt waits while the studio
 * is still checking a file in the Chhaap, and after a file is turned down until it is replaced.
 */
export function ContentComposer({ family, materials: materialsProp, item, prompt, duniya }: ContentComposerProps) {
  const router = useRouter();
  const titleId = useId();
  const materials = materialsProp.length > 0 ? materialsProp : FALLBACK_MATERIALS;
  const templates = family.templates;
  const [templateId, setTemplateId] = useState<string | undefined>(() => defaultTemplate(family, item?.template_id)?.id);
  const template = templates.find((t) => t.id === templateId);
  const allowed = useMemo(() => allowedMaterialIds(materials, family, template), [materials, family, template]);
  const [materialId, setMaterialId] = useState<string | undefined>(() => (item?.default_material && allowed.includes(item.default_material) ? item.default_material : allowed[0]));
  const [features, setFeatures] = useState<Feature[]>([]);
  const [title, setTitle] = useState(() => (prompt ?? item?.name ?? "").slice(0, 80));
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const uploads = useDesignStore((s) => s.uploads);
  const hold = uploadHold(features, uploads);
  const sending = useMemo(() => featuresForSubmit(features), [features]);

  // A template switch can change the finishes on offer; keep the selection valid.
  useEffect(() => {
    if (allowed.length > 0 && (!materialId || !allowed.includes(materialId))) setMaterialId(allowed[0]);
  }, [allowed, materialId]);

  const ready = family.available !== false && family.ready !== false && Boolean(template);
  const anchors = template ? contentAnchors(template) : [];
  const motifs = useMotifs(template ? templateTakes(template).includes("motif") : false).motifs;
  const look = presetStyle(duniya?.style, template);

  function pickTemplate(id: string) {
    setTemplateId(id);
    const next = templates.find((t) => t.id === id);
    // Keep what still fits: the spot exists on the new template and still takes that kind of content.
    setFeatures((list) => (next ? featuresFitting(list, next) : []));
  }

  async function sculpt() {
    if (!template || !materialId || busy || hold) return;
    setBusy(true);
    setProblem(undefined);
    try {
      const params = item && item.template_id === template.id ? primitiveParams(item.default_params) : undefined;
      const accepted = await api.designs.create({
        source: "create",
        family_id: family.id,
        template_id: template.id,
        features: sending,
        material: materialId,
        title: title.trim() || familyLabel(family),
        ...(params ? { params } : {}),
        ...(duniya ? { experience_id: duniya.id } : {}),
      });
      const query = new URLSearchParams({ job: accepted.job_id });
      if (duniya) query.set("duniya", duniya.slug);
      router.push(`/design/${accepted.design_id}?${query.toString()}`);
    } catch (err) {
      setProblem(toProblem(err));
      setBusy(false);
    }
  }

  // The template's parts are authoritative but carry only sku and qty; the names come from the family.
  const packed = packedHardware(family, template);
  const hardware = hardwareNames(packed);
  const from = priceFromLabel(family, formatPaise);

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <section className="ak-card grid content-start gap-6 p-6 sm:p-8" aria-labelledby="composer-heading">
        <header className="grid gap-2">
          {duniya && <DuniyaChip duniya={duniya} />}
          <span className="ak-eyebrow">{family.kind === "object" ? "Object" : "Avatar · the form your idea takes"}</span>
          <h1 id="composer-heading" className="font-display text-4xl font-semibold leading-none">
            {family.codename} <span className="text-surface-muted">· {family.name}</span>
          </h1>
          {family.tagline && <p className="font-display text-xl italic text-surface-muted">{family.tagline}</p>}
          {family.description && <p className="max-w-prose text-sm leading-relaxed">{family.description}</p>}
          {item && (
            <p className="text-[12px] text-surface-muted">
              Starting from <Link href={`/shop/${item.slug}`} className="underline underline-offset-2">{item.name}</Link>.
            </p>
          )}
        </header>

        {!ready ? (
          <div className="ak-well grid gap-2 p-5">
            <p className="font-semibold">{familyLabel(family)} is still being finished in the studio.</p>
            <p className="text-sm text-surface-muted">Pick another form for your idea for now; this one opens as soon as its template is live.</p>
            <Link href="/create" className="ak-btn ak-btn-secondary ak-btn-pill justify-self-start">
              Back to Create
            </Link>
          </div>
        ) : (
          <>
            {templates.length > 1 && (
              <fieldset className="grid gap-2">
                <legend className="ak-label mb-1">Which one?</legend>
                <div role="radiogroup" aria-label="Template" className="grid gap-2 sm:grid-cols-2">
                  {templates.map((t) => (
                    <button
                      key={t.id}
                      type="button"
                      role="radio"
                      aria-checked={t.id === templateId}
                      className="ak-chip min-h-11 justify-between whitespace-normal px-4 py-2 text-left"
                      onClick={() => pickTemplate(t.id)}
                      disabled={busy}
                    >
                      <span className="font-semibold">{t.name}</span>
                      <span className="text-[11px] opacity-70">{environmentLabel(t.environment)}</span>
                    </button>
                  ))}
                </div>
              </fieldset>
            )}

            {template && anchors.length > 0 ? (
              <ContentSlotPanel
                template={template}
                family={family}
                features={features}
                onChange={setFeatures}
                motifPack={duniya?.motifPack}
                motifPackLabel={duniya?.codename}
                disabled={busy}
              />
            ) : (
              <p className="text-sm text-surface-muted">This piece takes no personal content yet; its size and shape are yours to change in the studio.</p>
            )}

            <FinishChips materials={materials} value={materialId} onChange={setMaterialId} allowed={allowed} disabled={busy} />

            <div className="grid gap-1.5">
              <label htmlFor={titleId} className="text-xs text-surface-muted">
                Name your piece <span className="opacity-70">(optional)</span>
              </label>
              <input id={titleId} type="text" className="ak-input" value={title} maxLength={80} placeholder={familyLabel(family)} onChange={(e) => setTitle(e.target.value)} disabled={busy} />
            </div>

            <div className="grid gap-2">
              <button type="button" className="ak-btn ak-btn-primary ak-btn-pill justify-self-start px-8" onClick={sculpt} disabled={busy || !template || !materialId || Boolean(hold)} aria-busy={busy}>
                {busy ? "Sending to the studio…" : "Sculpt"}
              </button>
              <p className={hold ? "text-[12px] text-warning" : "text-[12px] text-surface-muted"} role={hold ? "status" : undefined}>
                {hold === "checking"
                  ? "The studio is checking a file you added. Sculpt opens as soon as it's cleared."
                  : hold === "rejected"
                    ? "A file you added can't be printed. Choose a different one to sculpt."
                    : sending.length === 0
                      ? `You can sculpt it plain and add ${CHHAAP.phrase} in the studio.`
                      : "Sculpt opens the studio, where size, finish and price are live."}
              </p>
              {problem && (
                <p role="alert" className="text-xs text-danger">
                  {problem.code === "upload_not_ready"
                    ? "The studio is still checking a file you added. You can sculpt as soon as it's cleared."
                    : problem.code === "upload_rejected"
                      ? REJECTED_COPY
                      : problem.code === "family_not_available"
                        ? `${familyLabel(family)} isn't open right now.`
                        : (problem.detail ?? problem.title)}
                </p>
              )}
            </div>
          </>
        )}
      </section>

      <aside className="ak-card grid content-start gap-4 p-5" aria-label="What you'll get">
        <h2 className="ak-label">What you&apos;ll get</h2>
        <dl className="grid gap-2 text-sm">
          <div className="grid gap-0.5">
            <dt className="text-[11px] text-surface-muted">Piece</dt>
            <dd className="font-semibold">{template ? template.name : familyLabel(family)}</dd>
          </div>
          {envelopeLine(family) && (
            <div className="grid gap-0.5">
              <dt className="text-[11px] text-surface-muted">Size</dt>
              <dd className="font-semibold">{envelopeLine(family)}</dd>
            </div>
          )}
          {hardware && (
            <div className="grid gap-0.5">
              <dt className="text-[11px] text-surface-muted">Comes with</dt>
              <dd className="font-semibold">{hardware}</dd>
            </div>
          )}
          <div className="grid gap-0.5">
            <dt className="text-[11px] text-surface-muted">{capitalise(CHHAAP.phrase)}</dt>
            <dd>
              {sending.length === 0 ? (
                <span className="text-surface-muted">Nothing yet</span>
              ) : (
                <ul className="grid gap-1">
                  {sending.map((f) => (
                    <li key={`${f.anchor}-${f.type}`} className="font-semibold">
                      {featureSummary(f, motifs)}
                    </li>
                  ))}
                </ul>
              )}
            </dd>
          </div>
          {duniya?.environment && (
            <div className="grid gap-0.5">
              <dt className="text-[11px] text-surface-muted">Shown on</dt>
              <dd className="font-semibold">{duniya.environmentLabel}</dd>
            </div>
          )}
          {look && (
            <div className="grid gap-0.5">
              <dt className="text-[11px] text-surface-muted">Look</dt>
              <dd className="font-semibold">{STYLE_LABELS[look]}</dd>
            </div>
          )}
          {from && (
            <div className="grid gap-0.5">
              <dt className="text-[11px] text-surface-muted">Price</dt>
              <dd className="font-semibold">{from}</dd>
            </div>
          )}
        </dl>
        <p className="text-[11px] leading-snug text-surface-muted">
          {hardwareSentence(packed) ?? "Printed to order in the finish you choose."} Nothing is printed until you pay.
        </p>
      </aside>
    </div>
  );
}
