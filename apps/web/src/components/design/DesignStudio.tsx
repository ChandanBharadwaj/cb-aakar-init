"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import { boundsMm, glbUrl, type DesignVersion, type Problem, type TemplateDescriptor } from "@/lib/api/types";
import { formatGrams, formatMm, formatPrintTime } from "@/lib/format";
import { environmentLabel } from "@/lib/viewer/environments";
import { selectActiveVersion, selectMaterial, useDesignStore } from "@/store/design";
import { BloomLoader } from "@/components/brand/BloomLoader";
import { MandalaSpinner } from "@/components/brand/MandalaSpinner";
import { StageNav } from "@/components/nav/StageNav";
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
}

type LoadStatus = "loading" | "ready" | "error";

function sameParams(a: Record<string, unknown>, b: Record<string, unknown>): boolean {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of keys) if (a[k] !== b[k]) return false;
  return true;
}

export function DesignStudio({ designId, jobId: urlJobId }: DesignStudioProps) {
  const router = useRouter();
  const store = useDesignStore();
  const [status, setStatus] = useState<LoadStatus>("loading");
  const [problem, setProblem] = useState<Problem>();
  const [template, setTemplate] = useState<TemplateDescriptor>();
  const [modelNotice, setModelNotice] = useState<string>();
  const [sculptProblem, setSculptProblem] = useState<Problem>();
  const [sculpting, setSculpting] = useState(false);

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
      router.replace(`/design/${designId}`);
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

  const dirty = activeVersion ? !sameParams(store.paramsDraft, activeVersion.spec.params) : false;
  const busy = Boolean(job && job.stage !== "failed");

  async function sculpt() {
    if (!activeVersion || !material) return;
    setSculpting(true);
    setSculptProblem(undefined);
    try {
      const accepted = await api.versions.editParams(activeVersion.id, { params: store.paramsDraft, material: material.id });
      useDesignStore.getState().startJob(accepted.job_id);
      router.replace(`/design/${designId}?job=${encodeURIComponent(accepted.job_id)}`);
    } catch (err) {
      setSculptProblem(toProblem(err));
    } finally {
      setSculpting(false);
    }
  }

  const price = store.price.status === "ready" ? store.price.price : store.price.status === "loading" || store.price.status === "error" ? store.price.previous : undefined;
  const bounds = boundsMm(activeVersion);
  const model = glbUrl(activeVersion);
  const environment = template?.environment;

  const tryAgainHref =
    design?.source === "shop" && design.catalog_item_slug
      ? `/design/new?item=${encodeURIComponent(design.catalog_item_slug)}`
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
        <StageNav section="Create" backHref={design.catalog_item_slug ? `/shop/${design.catalog_item_slug}` : "/shop"} backLabel="Back to the Shop">
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
      <div className="grid flex-1 grid-cols-1 lg:grid-cols-[280px_minmax(0,1fr)_312px]">
        {/* Left: title, karigar's note, params */}
        <aside className="ak-panel order-3 grid content-start gap-5 border-t border-surface-border p-5 lg:order-1 lg:border-r lg:border-t-0">
          <div className="grid gap-1.5">
            <div className="ak-eyebrow">{design.source === "shop" ? "From the Shop" : design.source === "remix" ? "Remix" : "Create"}</div>
            <h1 className="font-display text-3xl font-semibold leading-tight">{design.title}</h1>
            {template && (
              <p className="text-xs text-surface-muted">
                {template.name} · {environmentLabel(environment)}
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
              <ParamSliders
                params={template.params}
                values={store.paramsDraft}
                onChange={(k, v) => useDesignStore.getState().setParam(k, v)}
                disabled={busy || sculpting}
              />
              <div className="grid gap-2">
                <button type="button" className="ak-btn ak-btn-primary" onClick={sculpt} disabled={!dirty || busy || sculpting || !activeVersion} aria-busy={sculpting}>
                  {sculpting ? "Sending to the studio…" : "Sculpt"}
                </button>
                {dirty && activeVersion && (
                  <button type="button" className="ak-btn ak-btn-secondary min-h-9 text-xs" onClick={() => useDesignStore.getState().resetParams(activeVersion.spec.params)} disabled={busy}>
                    Undo changes
                  </button>
                )}
                {sculptProblem && (
                  <p role="alert" className="text-xs text-danger">
                    {sculptProblem.detail ?? sculptProblem.title}
                  </p>
                )}
              </div>
            </>
          ) : (
            <p className="text-xs text-surface-muted">Size and shape controls appear once the template descriptor loads.</p>
          )}
        </aside>

        {/* Centre: the stage */}
        <section className="relative order-1 min-h-[56dvh] lg:order-2 lg:min-h-[calc(100dvh-64px)]" aria-label="3D viewer">
          <div className="absolute inset-0">
            <DesignViewer
              glbUrl={model}
              pbr={(material ?? store.materials[0])?.pbr ?? { color: "#C4785A", roughness: 0.3, metalness: 0.1 }}
              environment={environment}
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
                  <p className="text-sm text-surface-muted">{job.message || "The studio couldn't finish this version."}</p>
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
                        router.replace(`/design/${designId}`);
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
        <aside className="ak-panel order-2 grid content-start gap-4 border-t border-surface-border p-5 lg:order-3 lg:border-l lg:border-t-0">
          <FinishChips materials={store.materials} value={material?.id} onChange={(id) => useDesignStore.getState().selectMaterial(id)} allowed={template?.materials} disabled={sculpting} />

          <div className="ak-well grid gap-1.5 p-3.5">
            <Stat label="Height" value={bounds ? formatMm(bounds[2]) : undefined} hint={bounds ? `${formatMm(bounds[0])} × ${formatMm(bounds[1])} × ${formatMm(bounds[2])}` : undefined} />
            <Stat label="Weight" value={price ? formatGrams(price.mass_g) : undefined} />
            <Stat label="Print time" value={activeVersion?.print_estimate ? formatPrintTime(activeVersion.print_estimate.print_seconds) : undefined} />
          </div>

          <StabilityCard report={activeVersion?.printability} pending={busy || activeVersion?.status === "generating"} />

          <PriceBreakdown price={price} loading={store.price.status === "loading"} error={store.price.status === "error" ? store.price.detail : undefined} />

          <button type="button" className="ak-btn ak-btn-primary" disabled title="Checkout arrives in Phase 1">
            Continue{price ? ` · ${formatPaise(price.total_paise)}` : ""}
          </button>
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
