"use client";

import Link from "next/link";
import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { AdminTemplate, Problem } from "@/lib/api/types";
import { FEATURE_TYPES, familyTitle, featureInfo } from "@/lib/families";
import { useQuery } from "@/lib/useQuery";
import { useCanWrite } from "@/store/session";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";
import { Switch } from "@/components/ui/Switch";

export function TemplatesPage() {
  const canWrite = useCanWrite();
  const { data, problem, reload, setData } = useQuery(() => api.templates.list(), "templates");
  const catalog = useQuery(() => api.catalog.list(), "catalog");
  const families = useQuery(() => api.families.list(), "families");
  const hardware = useQuery(() => api.hardware.list(), "hardware");
  const [busy, setBusy] = useState<string>();
  const [writeProblem, setWriteProblem] = useState<Problem>();

  async function setLive(t: AdminTemplate, live: boolean) {
    setBusy(t.id);
    setWriteProblem(undefined);
    try {
      await api.templates.setLive(t.id, { live });
      setData((prev) => prev?.map((x) => (x.id === t.id ? { ...x, live } : x)));
    } catch (err) {
      setWriteProblem(toProblem(err));
    } finally {
      setBusy(undefined);
    }
  }

  const familyOf = (id: string) => families.data?.find((f) => f.id === id);
  const hardwareName = (sku: string) => hardware.data?.find((h) => h.sku === sku)?.name ?? sku;

  return (
    <>
      <PageHeader eyebrow="Configuration" title="Templates" description="Parametric templates the geometry service knows, with their live flag, family (Avatar), the content (Chhaap) each accepts and the hardware its pockets are cut for. A template that isn't live is hidden from Create and blocks new designs; existing designs keep working." />
      <OwnerOnlyHint what="Live toggles" className="mb-4" />
      {writeProblem && <ProblemCard compact problem={writeProblem} className="mb-4" />}
      {problem && !data ? (
        <ProblemCard problem={problem} action={{ label: "Try again", onClick: () => void reload() }} />
      ) : !data ? (
        <Loading />
      ) : data.length === 0 ? (
        <EmptyState title="No templates registered">The geometry service hasn&apos;t published any template descriptors yet.</EmptyState>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {data.map((t) => {
            const using = catalog.data?.filter((c) => c.template_id === t.id) ?? t.catalog_items?.map((slug) => ({ slug, name: slug, available: undefined })) ?? [];
            const family = familyOf(t.family);
            const features = FEATURE_TYPES.filter((f) => t.features_supported?.includes(f.id));
            const unknownFeatures = (t.features_supported ?? []).filter((f) => !FEATURE_TYPES.some((x) => x.id === f));
            return (
              <li key={t.id} className="ak-card grid gap-3 p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="grid gap-0.5">
                    <span className="font-display text-2xl font-semibold leading-tight">{t.name}</span>
                    <span className="font-mono text-[12px] text-surface-muted">
                      {t.id}@{t.version}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Pill tone={t.live ? "success" : "warning"}>{t.live ? "Live" : "Not live"}</Pill>
                    <Switch label={`${t.name} live`} checked={t.live} onChange={(v) => void setLive(t, v)} disabled={!canWrite} busy={busy === t.id} />
                  </div>
                </div>
                <div className="grid gap-1.5">
                  <span className="ak-label">Family</span>
                  {family ? (
                    <Link href="/avatars" className="text-sm hover:underline" title={`Avatar ${family.id}${family.available ? "" : " · hidden from the picker"}`}>
                      {familyTitle(family)} <span className="font-mono text-[11px] text-surface-muted">{t.family}</span>
                    </Link>
                  ) : (
                    <span className="text-sm" title={families.data ? "No Avatar row with this id; add one on the Avatars page" : undefined}>
                      <span className="font-mono text-[12.5px]">{t.family}</span>
                      {families.data && <span className="ml-1 text-[11px] text-warning">not an Avatar yet</span>}
                    </span>
                  )}
                </div>
                <div className="grid gap-1.5">
                  <span className="ak-label">Chhaap it accepts</span>
                  {features.length === 0 && unknownFeatures.length === 0 ? (
                    <span className="text-sm text-surface-muted">{t.features_supported ? "None: the body only." : "Not reported by the API."}</span>
                  ) : (
                    <ul className="flex flex-wrap gap-1.5" aria-label="Supported content features">
                      {features.map((f) => (
                        <li key={f.id} className="ak-chip min-h-8 cursor-default py-0.5 text-[12px]" title={`${f.id} · ${f.label}`}>
                          {f.codename}
                        </li>
                      ))}
                      {unknownFeatures.map((f) => (
                        <li key={f} className="ak-chip min-h-8 cursor-default py-0.5 font-mono text-[12px]" title={featureInfo(f).label}>
                          {f}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
                {t.hardware && t.hardware.length > 0 && (
                  <div className="grid gap-1.5">
                    <span className="ak-label">Hardware</span>
                    <ul className="flex flex-wrap gap-1.5" aria-label="Hardware the template is cut for">
                      {t.hardware.map((h) => (
                        <li key={h.sku}>
                          <Link href="/hardware" className="ak-chip min-h-8 py-0.5 text-[12px]" title={h.sku}>
                            {hardwareName(h.sku)}
                            <span className="opacity-60">× {h.qty}</span>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                <div className="grid gap-1.5">
                  <span className="ak-label">Catalog items using it</span>
                  {using.length === 0 ? (
                    <span className="text-sm text-surface-muted">None yet.</span>
                  ) : (
                    <ul className="flex flex-wrap gap-1.5">
                      {using.map((c) => (
                        <li key={c.slug}>
                          <Link href="/catalog" className="ak-chip min-h-8 py-0.5 text-[12px]" title={c.available === false ? "Coming soon in the Shop" : undefined}>
                            {c.name}
                            {c.available === false && <span className="opacity-60">· coming soon</span>}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
}
