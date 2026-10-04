"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import { boundsMm, glbUrl, type DesignVersion, type Environment, type Experience, type Family, type PrintabilityReport, type Problem, type TemplateDescriptor } from "@/lib/api/types";
import { duniyaPreset, presetStyle, STYLE_LABELS, type DuniyaPreset } from "@/lib/experiences";
import { allowedMaterialIds, hardwareNames, isRawFamily, namedHardware, RAW_FAMILY_ID } from "@/lib/families";
import { familyLabel, featuresForSubmit, featuresFromSpec, hasContentSlot, missingContent, missingContentLine, sameFeatures } from "@/lib/features";
import { formatGrams, formatMm, formatPrintTime } from "@/lib/format";
import { REJECTED_COPY } from "@/lib/uploads";
import { environmentLabel } from "@/lib/viewer/environments";
import { useCartStore } from "@/store/cart";
import { selectActiveVersion, selectMaterial, uploadHold, useDesignStore } from "@/store/design";
import { toast } from "@/store/toast";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { MandalaSpinner } from "@/components/brand/MandalaSpinner";
import { StageNav } from "@/components/nav/StageNav";
import { ContentSlotPanel } from "@/components/design/ContentSlotPanel";
import { DuniyaChip } from "@/components/duniya/DuniyaChip";
import { FinishChips } from "@/components/ui/FinishChips";
import { ParamSliders } from "@/components/ui/ParamSliders";
import { PriceBreakdown } from "@/components/ui/PriceBreakdown";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { StabilityCard } from "@/components/ui/StabilityCard";
import { StageTimeline } from "@/components/ui/StageTimeline";
import { Stat } from "@/components/ui/Stat";
import { useJobStream } from "./useJobStream";

// three.js needs a window; keep the Canvas out of the server render.
const DesignViewer = dynamic(() => import("@/components/viewer/DesignViewer").then((m) => m.DesignViewer), {
  ssr: false,
  loading: () => (
    <div className="grid h-full w-full place-items-center bg-indigo-deep">
      <BloomLoader size={88} label={null} />
    </div>
  ),
});

export interface DesignStudioProps {
  designId: string;
  /** `?job=` from the URL: the generation job to follow. */
  jobId?: string;
  /**
   * `?duniya=` from the URL: the slug of the Duniya experience the piece is made for. Without it the studio falls back
   * to the design's own `experience_id`.
   */
  duniya?: string;
}

type LoadStatus = "loading" | "ready" | "error";

/** `/design/{id}` with the job to follow and the Duniya slug kept, so a reload lands on the same themed studio. */
function studioHref(designId: string, query: { job?: string; duniya?: string }): string {
  const params = new URLSearchParams();
  if (query.job) params.set("job", query.job);
  if (query.duniya) params.set("duniya", query.duniya);
  const qs = params.toString();
  return `/design/${designId}${qs ? `?${qs}` : ""}`;
}

/**
 * The Duniya a studio shows: the slug from the URL, else the experience the design was started from (looked up among
 * the open experiences by id). Anything unknown, closed or unreachable presets nothing.
 */
async function resolveDuniya(slug: string | undefined, experienceId: string | null | undefined): Promise<DuniyaPreset | undefined> {
  let experience: Experience | undefined;
  if (slug) experience = await api.experiences.get(slug).catch(() => undefined);
  if (!experience && experienceId) experience = (await api.experiences.list().catch(() => [] as Experience[])).find((x) => x.id === experienceId);
  if (!experience) return undefined;
  const environments = await api.environments.list().catch(() => undefined as Environment[] | undefined);
  return duniyaPreset(experience, environments);
}

function sameParams(a: Record<string, unknown>, b: Record<string, unknown>): boolean {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of keys) if (a[k] !== b[k]) return false;
  return true;
}

/**
 * What a raw print that failed the check needs, pointed at the one control that fixes it: the size slider on the
 * "Your form" card (Swaroop has no template sliders).
 */
function rawNudge(report: PrintabilityReport | undefined, minWallMm: number): string | undefined {
  if (report?.passed !== false) return undefined;
  if (report.checks.fits_bed?.status === "fail") return "It's too big for the printer; make it smaller with the size slider on Your form.";
  return `Walls under ${formatMm(minWallMm, 1)} won't print; make it larger with the size slider on Your form.`;
}

export function DesignStudio({ designId, jobId: urlJobId, duniya: duniyaSlug }: DesignStudioProps) {
  const router = useRouter();
  const store = useDesignStore();
  const [status, setStatus] = useState<LoadStatus>("loading");
  const [problem, setProblem] = useState<Problem>();
  const [template, setTemplate] = useState<TemplateDescriptor>();
  const [family, setFamily] = useState<Family>();
  const [modelNotice, setModelNotice] = useState<string>();
  const [sculptProblem, setSculptProblem] = useState<Problem>();
  const [sculpting, setSculpting] = useState(false);
  const [adding, setAdding] = useState(false);
  const [addProblem, setAddProblem] = useState<Problem>();
  const [duniya, setDuniya] = useState<DuniyaPreset>();
  const cartCount = useCartStore((s) => s.count);

  const reload = useCallback(async () => {
    const s = useDesignStore.getState();
    const design = await api.designs.get(designId);
    s.setDesign(design);
    try {
      s.setVersions(await api.designs.versions(designId));
    } catch {
      /* versions are a nicety; the latest version is on the design */
    }
    return design;
  }, [designId]);

  // Initial load: design (fatal), versions, materials and the template descriptor (all non-fatal).
  useEffect(() => {
    const s = useDesignStore.getState();
    s.reset();
    let cancelled = false;
    setStatus("loading");
    setProblem(undefined);
    setTemplate(undefined);
    setFamily(undefined);
    setModelNotice(undefined);

    api.catalog.materials().then((m) => !cancelled && m.length > 0 && s.setMaterials(m)).catch(() => undefined);

    (async () => {
      try {
        const design = await api.designs.get(designId);
        if (cancelled) return;
        s.setDesign(design);
        setStatus("ready");
        api.designs.versions(designId).then((v) => !cancelled && s.setVersions(v)).catch(() => undefined);
        const templateId = design.latest_version?.template?.id ?? design.latest_version?.spec.template.split("@")[0];
        if (templateId) api.templates.get(templateId).then((t) => !cancelled && setTemplate(t)).catch(() => undefined);
      } catch (err) {
        if (cancelled) return;
        setProblem(toProblem(err));
        setStatus("error");
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [designId]);

  const design = store.design;
  const activeVersion = selectActiveVersion(store);
  const material = selectMaterial(store);
  const job = store.job;

  // The Avatar this piece belongs to (design, version, or the spec's family id), fetched lazily for its codename and rules.
  const familyId = design?.family_id ?? activeVersion?.family_id ?? activeVersion?.spec.family;
  useEffect(() => {
    if (!familyId) return;
    let cancelled = false;
    api.families
      .get(familyId)
      .then((f) => !cancelled && setFamily(f))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [familyId]);

  // The Duniya this piece is made for: its backdrop on the stage, its motif pack first in Buti, its chip in the panel.
  const experienceId = design?.experience_id;
  useEffect(() => {
    if (!duniyaSlug && !experienceId) {
      setDuniya(undefined);
      return;
    }
    let cancelled = false;
    resolveDuniya(duniyaSlug, experienceId).then((d) => !cancelled && setDuniya(d));
    return () => {
      cancelled = true;
    };
  }, [duniyaSlug, experienceId]);

  // Follow the job from the URL, or the one the design says is still generating.
  const effectiveJobId = urlJobId ?? (design?.status === "generating" ? design.latest_version?.job_id : undefined);

  useEffect(() => {
    if (effectiveJobId && useDesignStore.getState().job?.id !== effectiveJobId) useDesignStore.getState().startJob(effectiveJobId);
  }, [effectiveJobId]);

  useJobStream(effectiveJobId, {
    onEvent: (ev) => useDesignStore.getState().applyStageEvent(ev),
    onDone: async (ev) => {
      if (ev.stage !== "ready") return;
      try {
        const fresh = await reload();
        const s = useDesignStore.getState();
        if (fresh.latest_version) s.setActiveVersion(fresh.latest_version.id);
        s.clearJob();
        setModelNotice(undefined);
      } catch (err) {
        setProblem(toProblem(err));
      }
      router.replace(studioHref(designId, { duniya: duniyaSlug }));
    },
  });

  // Price for the version on stage in the selected finish (instant when the version already carries it).
  useEffect(() => {
    const s = useDesignStore.getState();
    if (!activeVersion || !material) return;
    if (activeVersion.status !== "ready") {
      s.setPrice({ status: "idle" });
      return;
    }
    if (activeVersion.price && activeVersion.price.material_id === material.id) {
      s.setPrice({ status: "ready", price: activeVersion.price });
      return;
    }
    let cancelled = false;
    const previous = s.price.status === "ready" ? s.price.price : s.price.status === "loading" || s.price.status === "error" ? s.price.previous : undefined;
    s.setPrice({ status: "loading", previous });
    api.versions
      .price(activeVersion.id, material.id)
      .then((price) => !cancelled && s.setPrice({ status: "ready", price }))
      .catch((err) => {
        if (cancelled) return;
        const p = toProblem(err);
        s.setPrice({ status: "error", detail: p.code === "version_not_ready" ? "Pricing once the piece is checked." : (p.detail ?? "Couldn't price this finish."), previous });
      });
    return () => {
      cancelled = true;
    };
  }, [activeVersion, material]);

  const versionsAsc = useMemo<DesignVersion[]>(() => {
    const list = store.versions.length > 0 ? store.versions : design?.latest_version ? [design.latest_version] : [];
    return [...list].sort((a, b) => a.version_no - b.version_no);
  }, [store.versions, design]);
  const versionIndex = activeVersion ? versionsAsc.findIndex((v) => v.id === activeVersion.id) : -1;
  const prevVersion = versionIndex > 0 ? versionsAsc[versionIndex - 1] : undefined;
  const nextVersion = versionIndex >= 0 ? versionsAsc[versionIndex + 1] : undefined;

  // Swaroop: no template params; its size and orientation live on the one hero form, which can't be removed.
  const raw = familyId === RAW_FAMILY_ID || (family ? isRawFamily(family) : false);

  // Dirty when the sliders or the Chhaap (as it would be sent) differ from the version on stage; one Sculpt sends both.
  const sending = useMemo(() => featuresForSubmit(store.featuresDraft), [store.featuresDraft]);
  const paramsDirty = activeVersion && !raw ? !sameParams(store.paramsDraft, activeVersion.spec.params) : false;
  const featuresDirty = activeVersion ? !sameFeatures(sending, featuresFromSpec(activeVersion.spec)) : false;
  const dirty = paramsDirty || featuresDirty;
  const busy = Boolean(job && job.stage !== "failed");
  const hold = uploadHold(store.featuresDraft, store.uploads);
  const needsForm = raw && !store.featuresDraft.some((f) => f.type === "hero_mesh");
  // A spot the piece can't be made without (an anchor's `required`: Roshni's photo) must keep its content.
  const need = template && !raw ? missingContentLine(missingContent(template, sending), template) : undefined;

  // Finishes: the family's material_rules and the template's list, the same rule the composers apply.
  const allowedFinishes = useMemo(() => allowedMaterialIds(store.materials, family, template), [store.materials, family, template]);
  useEffect(() => {
    if (!material || allowedFinishes.length === 0 || allowedFinishes.includes(material.id)) return;
    const first = allowedFinishes[0];
    if (first) useDesignStore.getState().selectMaterial(first);
  }, [material, allowedFinishes]);

  async function sculpt() {
    if (!activeVersion || !material || hold || needsForm || need) return;
    setSculpting(true);
    setSculptProblem(undefined);
    try {
      const accepted = await api.versions.editParams(activeVersion.id, { params: raw ? {} : store.paramsDraft, material: material.id, features: sending });
      useDesignStore.getState().startJob(accepted.job_id);
      router.replace(studioHref(designId, { job: accepted.job_id, duniya: duniyaSlug }));
    } catch (err) {
      setSculptProblem(toProblem(err));
    } finally {
      setSculpting(false);
    }
  }

  function undo() {
    if (!activeVersion) return;
    const s = useDesignStore.getState();
    s.resetParams(activeVersion.spec.params);
    s.resetFeatures(activeVersion.spec);
  }

  const price = store.price.status === "ready" ? store.price.price : store.price.status === "loading" || store.price.status === "error" ? store.price.previous : undefined;

  // Add to Cart: the version on stage, in the selected finish. Disabled with a reason until it is ready and stable.
  const addReason = !activeVersion || activeVersion.status !== "ready"
    ? "Add to Cart once the piece is sculpted and checked."
    : activeVersion.printability?.passed === false
      ? "Fix the stability notes before adding this piece."
      : busy
        ? "Wait for the studio to finish this version."
        : dirty
          ? "Sculpt your changes first, or undo them, to add exactly what you see."
          : undefined;

  async function addToCart() {
    if (!activeVersion || !material || addReason) return;
    setAdding(true);
    setAddProblem(undefined);
    try {
      await useCartStore.getState().add(activeVersion.id, material.id, 1);
      toast({ message: `${design?.title ?? "Your piece"} in ${material.name} is in your cart.`, action: { href: "/cart", label: "View cart" }, tone: "success" });
    } catch (err) {
      setAddProblem(toProblem(err));
    } finally {
      setAdding(false);
    }
  }

  const bounds = boundsMm(activeVersion);
  const model = glbUrl(activeVersion);
  // A Duniya's backdrop wins when the viewer has its preset; otherwise the piece keeps its own.
  const environment = duniya?.environment ?? template?.environment ?? family?.environment;
  // The look on stage and in the Chhaap: the design's own style, else (while it carries none) the Duniya's preset when
  // the template offers it. comic_pop draws the piece cel-shaded with ink outlines.
  const specLook = activeVersion?.spec.style;
  const look = specLook && specLook !== "none" ? specLook : presetStyle(duniya?.style, template);
  const hardware = hardwareNames(namedHardware(activeVersion?.hardware, family));
  const minWall = template?.constraints.min_wall_mm ?? activeVersion?.spec.constraints?.min_wall_mm ?? 1.2;
  const nudge = raw && !busy ? rawNudge(activeVersion?.printability, minWall) : undefined;

  const eyebrow = family
    ? familyLabel(family)
    : design?.source === "shop"
      ? "From the Shop"
      : design?.source === "remix"
        ? "Remix"
        : design?.source === "upload"
          ? "Print as it is"
          : "Create";

  const fromCreate = design?.source === "create" || design?.source === "upload";
  const composerHref = familyId ? `/create/${encodeURIComponent(familyId)}${duniya ? `?duniya=${encodeURIComponent(duniya.slug)}` : ""}` : undefined;
  const backHref = design?.catalog_item_slug ? `/shop/${design.catalog_item_slug}` : fromCreate && composerHref ? composerHref : "/shop";
  const backLabel = design?.catalog_item_slug ? "Back to the Shop" : fromCreate && familyId ? "Back to Create" : "Back to the Shop";

  const tryAgainHref =
    design?.source === "shop" && design.catalog_item_slug
      ? `/design/new?item=${encodeURIComponent(design.catalog_item_slug)}`
      : fromCreate && composerHref
        ? composerHref
        : template
          ? `/design/new?template=${encodeURIComponent(template.id)}`
          : "/shop";

  if (status === "loading") {
    return (
      <StageShell>
        <div className="grid flex-1 place-items-center p-6">
          <BloomLoader size={132} label="Opening your piece" />
        </div>
      </StageShell>
    );
  }

  if (status === "error" || !design) {
    return (
      <StageShell>
        <div className="mx-auto w-full max-w-lg p-6">
          <ProblemCard problem={problem ?? { title: "Couldn't open this design", code: "unknown" }} action={{ href: "/shop", label: "Back to the Shop" }} />
        </div>
      </StageShell>
    );
  }

  return (
    <StageShell
      nav={
        <StageNav section="Create" backHref={backHref} backLabel={backLabel}>
          <div className="flex items-center gap-1 text-xs text-surface-muted" aria-label="Versions">
            <button
              type="button"
              className="ak-btn ak-btn-secondary min-h-8 px-2 py-1 text-xs"
              disabled={!prevVersion || busy}
              onClick={() => prevVersion && useDesignStore.getState().setActiveVersion(prevVersion.id)}
              aria-label="Previous version"
            >
              ‹
            </button>
            <span className="min-w-[7.5rem] text-center">
              Version {activeVersion?.version_no ?? design.versions_count} of {Math.max(design.versions_count, versionsAsc.length)}
            </span>
            <button
              type="button"
              className="ak-btn ak-btn-secondary min-h-8 px-2 py-1 text-xs"
              disabled={!nextVersion || busy}
              onClick={() => nextVersion && useDesignStore.getState().setActiveVersion(nextVersion.id)}
              aria-label="Next version"
            >
              ›
            </button>
          </div>
        </StageNav>
      }
    >
      <div className="grid flex-1 grid-cols-1 lg:flex-none lg:h-[calc(100dvh-64px)] lg:min-h-0 lg:grid-cols-[280px_minmax(0,1fr)_312px] lg:grid-rows-[minmax(0,1fr)] lg:overflow-hidden">
        {/* Left: title, karigar's note, params, the Chhaap */}
        <aside className="ak-panel order-3 grid content-start gap-5 border-t border-surface-border p-5 lg:order-1 lg:min-h-0 lg:overflow-y-auto lg:border-r lg:border-t-0">
          <div className="grid gap-1.5">
            {duniya && <DuniyaChip duniya={duniya} />}
            <div className="ak-eyebrow">{eyebrow}</div>
            <h1 className="font-display text-3xl font-semibold leading-tight">{design.title}</h1>
            {template && (
              <p className="text-xs text-surface-muted">
                {template.name} · {duniya?.environment ? duniya.environmentLabel : environmentLabel(environment)}
                {look ? ` · ${STYLE_LABELS[look]}` : ""}
              </p>
            )}
          </div>

          {activeVersion?.karigar_note && (
            <div className="ak-well grid gap-1.5 p-3.5 text-[13px] leading-relaxed">
              <div className="ak-label">Your karigar&apos;s note</div>
              <p>{activeVersion.karigar_note}</p>
            </div>
          )}

          {template ? (
            <>
              {/* Swaroop's size and orientation sit on the "Your form" card; its template has no sliders of its own. */}
              {!raw && (
                <ParamSliders
                  params={template.params}
                  values={store.paramsDraft}
                  onChange={(k, v) => useDesignStore.getState().setParam(k, v)}
                  disabled={busy || sculpting}
                />
              )}
              {hasContentSlot(template) && (
                <ContentSlotPanel
                  template={template}
                  family={family}
                  features={store.featuresDraft}
                  onChange={(edit) => useDesignStore.getState().updateFeatures(edit)}
                  motifPack={duniya?.motifPack}
                  motifPackLabel={duniya?.codename}
                  raw={raw}
                  look={look}
                  disabled={busy || sculpting}
                />
              )}
              <div className="grid gap-2">
                <button
                  type="button"
                  className="ak-btn ak-btn-primary"
                  onClick={sculpt}
                  disabled={!dirty || busy || sculpting || !activeVersion || Boolean(hold) || needsForm || Boolean(need)}
                  aria-busy={sculpting}
                >
                  {sculpting ? "Sending to the studio…" : "Sculpt"}
                </button>
                {(hold || needsForm || need) && (
                  <p role="status" className="text-[11px] leading-snug text-warning">
                    {hold === "checking"
                      ? "The studio is checking a file you added. Sculpt opens as soon as it's cleared."
                      : hold === "rejected"
                        ? "A file you added can't be printed. Choose a different one to sculpt."
                        : needsForm
                          ? "Add your model file to sculpt."
                          : need}
                  </p>
                )}
                {dirty && activeVersion && (
                  <button type="button" className="ak-btn ak-btn-secondary min-h-9 text-xs" onClick={undo} disabled={busy}>
                    Undo changes
                  </button>
                )}
                {sculptProblem && (
                  <p role="alert" className="text-xs text-danger">
                    {sculptProblem.code === "upload_not_ready"
                      ? "The studio is still checking a file you added. You can sculpt as soon as it's cleared."
                      : sculptProblem.code === "upload_rejected"
                        ? REJECTED_COPY
                        : (sculptProblem.detail ?? sculptProblem.title)}
                  </p>
                )}
              </div>
            </>
          ) : (
            <p className="text-xs text-surface-muted">Size and shape controls appear once the template descriptor loads.</p>
          )}
        </aside>

        {/* Centre: the stage */}
        <section className="relative order-1 min-h-[56dvh] lg:order-2 lg:h-full lg:min-h-0" aria-label="3D viewer">
          <div className="absolute inset-0">
            <DesignViewer
              glbUrl={model}
              pbr={(material ?? store.materials[0])?.pbr ?? { color: "#C4785A", roughness: 0.3, metalness: 0.1 }}
              environment={environment}
              look={look}
              dimmed={busy}
              onModelError={() => setModelNotice("The preview couldn't be loaded, so you're looking at a stand-in form.")}
            />
          </div>

          <div className="pointer-events-none absolute inset-x-0 top-0 flex items-start justify-between gap-3 p-4">
            <div className="pointer-events-auto grid gap-1">
              {activeVersion?.status === "ready" && !model && <Notice>No preview for this version yet.</Notice>}
              {activeVersion?.status === "failed" && !job && <Notice>This version couldn&apos;t be sculpted. Step back to an earlier one.</Notice>}
              {modelNotice && <Notice>{modelNotice}</Notice>}
            </div>
            <button
              type="button"
              className="ak-btn ak-btn-pill pointer-events-auto min-h-10 bg-paper px-4 text-xs text-ink shadow-float disabled:opacity-80"
              disabled
              title="AR arrives in Phase 2"
            >
              <span aria-hidden="true">▢</span> View in my room
            </button>
          </div>

          <div className="pointer-events-none absolute inset-x-0 bottom-0 flex items-end justify-between gap-3 p-4 text-[11px] text-surface-muted">
            <span className="hidden sm:block">Drag to turn · scroll to zoom · double-click to reset</span>
            {material && (
              <span className="ak-well pointer-events-auto rounded-pill px-3 py-1.5 font-semibold text-surface-text">
                {material.name}
                {price ? ` · ${formatGrams(price.mass_g)}` : ""}
              </span>
            )}
          </div>

          {job && (
            <div className="absolute inset-0 grid place-items-center bg-indigo-deep/70 p-6 backdrop-blur-sm animate-fade-in" role="status">
              {job.stage === "failed" ? (
                <div className="ak-card grid max-w-sm gap-4 p-6 text-center">
                  <div className="font-display text-2xl font-semibold">Something went wrong</div>
                  <p className="text-sm text-surface-muted">
                    {job.errorCode === "content_unusable" ? "We couldn't repair this file. Try another export from your 3D program." : job.message || "The studio couldn't finish this version."}
                  </p>
                  {job.errorCode && <span className="font-mono text-[11px] text-surface-muted">{job.errorCode}</span>}
                  <div className="flex justify-center gap-2">
                    <Link href={tryAgainHref} className="ak-btn ak-btn-primary ak-btn-pill">
                      Try again
                    </Link>
                    <button
                      type="button"
                      className="ak-btn ak-btn-secondary ak-btn-pill"
                      onClick={() => {
                        useDesignStore.getState().clearJob();
                        router.replace(studioHref(designId, { duniya: duniyaSlug }));
                      }}
                    >
                      Keep looking
                    </button>
                  </div>
                </div>
              ) : (
                <div className="grid w-full max-w-md gap-6">
                  <MandalaSpinner stage={job.stage} message={job.message} percent={job.percent} />
                  <StageTimeline stage={job.stage} />
                </div>
              )}
            </div>
          )}
        </section>

        {/* Right: finishes, stats, stability, price */}
        <aside className="ak-panel order-2 grid content-start gap-4 border-t border-surface-border p-5 lg:order-3 lg:min-h-0 lg:overflow-y-auto lg:border-l lg:border-t-0">
          <FinishChips materials={store.materials} value={material?.id} onChange={(id) => useDesignStore.getState().selectMaterial(id)} allowed={allowedFinishes} disabled={sculpting} />

          <div className="ak-well grid gap-1.5 p-3.5">
            <Stat label="Height" value={bounds ? formatMm(bounds[2]) : undefined} hint={bounds ? `${formatMm(bounds[0])} × ${formatMm(bounds[1])} × ${formatMm(bounds[2])}` : undefined} />
            <Stat label="Weight" value={price ? formatGrams(price.mass_g) : undefined} />
            <Stat label="Print time" value={activeVersion?.print_estimate ? formatPrintTime(activeVersion.print_estimate.print_seconds) : undefined} />
            {hardware && <Stat label="Comes with" value={hardware} />}
          </div>

          <StabilityCard report={activeVersion?.printability} pending={busy || activeVersion?.status === "generating"} />
          {nudge && (
            <p role="status" className="text-xs leading-snug text-warning">
              {nudge}
            </p>
          )}

          <PriceBreakdown price={price} loading={store.price.status === "loading"} error={store.price.status === "error" ? store.price.detail : undefined} />

          <div className="grid gap-2">
            <button type="button" className="ak-btn ak-btn-primary" onClick={addToCart} disabled={Boolean(addReason) || adding || !material} title={addReason} aria-busy={adding}>
              {adding ? "Adding…" : `Add to Cart${price ? ` · ${formatPaise(price.subtotal_paise)}` : ""}`}
            </button>
            {addReason && <p className="text-[11px] leading-snug text-surface-muted">{addReason}</p>}
            {addProblem && (
              <p role="alert" className="text-xs text-danger">
                {addProblem.code === "not_printable" ? "The studio says this version isn't printable yet." : (addProblem.detail ?? addProblem.title)}
              </p>
            )}
            {cartCount > 0 && (
              <Link href="/cart" className="ak-btn ak-btn-secondary min-h-9 text-xs">
                Go to cart · {cartCount}
              </Link>
            )}
          </div>
        </aside>
      </div>
    </StageShell>
  );
}

function StageShell({ nav, children }: { nav?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      {nav ?? <StageNav section="Create" />}
      {children}
    </div>
  );
}

function Notice({ children }: { children: React.ReactNode }) {
  return <span className="ak-well inline-block rounded-pill px-3 py-1.5 text-[11px] font-semibold text-surface-text">{children}</span>;
}
