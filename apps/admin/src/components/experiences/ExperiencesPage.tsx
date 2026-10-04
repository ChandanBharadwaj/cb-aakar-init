"use client";

import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { AdminExperience, Problem } from "@/lib/api/types";
import { environmentLabel, presetBuilt } from "@/lib/environments";
import { experienceInput, formatWindow, inSeason, sortExperiences, studioToday, styleLabel } from "@/lib/experiences";
import { familyTitle } from "@/lib/families";
import { formatDateTime } from "@/lib/format";
import { useQuery } from "@/lib/useQuery";
import { useCanWrite } from "@/store/session";
import { ExperienceDrawer } from "./ExperienceDrawer";
import { PaletteSwatches } from "@/components/environments/Backdrops";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";
import { Switch } from "@/components/ui/Switch";

/**
 * Duniya · Experiences: the Shop's themes. Each row ties a backdrop, a default style, a motif pack, ordered Avatars,
 * curated Shop items and seasons together. Available lists it on the Shop's Duniya persona (an unavailable one's page
 * says "coming soon"); only Avatars that are open (available with a live template) show there.
 */
export function ExperiencesPage() {
  const canWrite = useCanWrite();
  const experiences = useQuery(() => api.experiences.list(), "experiences");
  const environments = useQuery(() => api.environments.list(), "environments");
  const families = useQuery(() => api.families.list(), "families");
  const catalog = useQuery(() => api.catalog.list(), "catalog");
  const motifs = useQuery(() => api.motifs.list(), "motifs");
  const [editing, setEditing] = useState<AdminExperience | null | undefined>(undefined);
  const [toggling, setToggling] = useState<string>();
  const [problem, setProblem] = useState<Problem>();

  function saved(e: AdminExperience) {
    experiences.setData((prev) => {
      const list = prev ?? [];
      return sortExperiences(list.some((x) => x.id === e.id) ? list.map((x) => (x.id === e.id ? e : x)) : [...list, e]);
    });
    setEditing(undefined);
  }

  async function toggleAvailable(e: AdminExperience, available: boolean) {
    setToggling(e.id);
    setProblem(undefined);
    try {
      saved(await api.experiences.update(e.id, { ...experienceInput(e), available }));
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setToggling(undefined);
    }
  }

  const rows = experiences.data ? sortExperiences(experiences.data) : undefined;
  const today = studioToday();
  const familyOf = (id: string) => families.data?.find((f) => f.id === id);
  const envOf = (id: string) => environments.data?.find((e) => e.id === id);
  // Only families that are available with a live template show on the Shop; the rest wait in the list.
  const isOpen = (id: string) => {
    const f = familyOf(id);
    return Boolean(f && f.available && f.ready);
  };
  const lookupsReady = Boolean(environments.data && families.data);

  return (
    <>
      <PageHeader
        eyebrow="Configuration"
        title="Duniya · Experiences"
        description="Themes on the Shop's second persona: each pairs a backdrop (Mahaul), a default style and a motif pack (Buti) with ordered Avatars, curated Shop items and seasons. Codename and copy are brand data edited here; ids never change, slugs are the page URL (/duniya/<slug>). Available lists a Duniya on the Shop; a hidden one's page says coming soon."
        actions={
          <button type="button" className="ak-btn ak-btn-primary ak-btn-sm" onClick={() => setEditing(null)} disabled={!canWrite || !lookupsReady} title={canWrite ? undefined : "Owner only"}>
            New Duniya
          </button>
        }
      />
      <OwnerOnlyHint what="Duniya edits" className="mb-4" />
      {problem && <ProblemCard compact problem={problem} className="mb-4" title={problem.code === "forbidden" ? "Owner only" : undefined} />}
      {experiences.problem && !experiences.data ? (
        <ProblemCard problem={experiences.problem} action={{ label: "Try again", onClick: () => void experiences.reload() }} />
      ) : !rows ? (
        <Loading />
      ) : rows.length === 0 ? (
        <EmptyState title="No Duniya yet">The seed in packages/design-tokens/experiences.json has not been loaded. Create one to start.</EmptyState>
      ) : (
        <div className="ak-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="ak-table">
              <thead>
                <tr>
                  <th>Duniya</th>
                  <th>Backdrop</th>
                  <th>Style</th>
                  <th>Seasons</th>
                  <th className="num">Avatars</th>
                  <th className="num">Items</th>
                  <th>Available</th>
                  <th className="num">Sort</th>
                  <th>Updated</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((e) => {
                  const env = envOf(e.environment);
                  const open = e.avatars.filter(isOpen);
                  const avatarsTitle = e.avatars
                    .map((id) => {
                      const f = familyOf(id);
                      return `${f ? familyTitle(f) : id}${isOpen(id) ? "" : " (not open)"}`;
                    })
                    .join("\n");
                  return (
                    <tr key={e.id} className={e.available ? "" : "opacity-70"}>
                      <td>
                        <div className="grid gap-0.5">
                          <span className="flex items-center gap-2 font-medium">
                            <span aria-hidden="true" className="h-2.5 w-2.5 flex-none rounded-full border border-black/10" style={{ background: e.surface.accent }} />
                            {e.codename} <span className="text-surface-muted">·</span> {e.title}
                          </span>
                          <span className="font-mono text-[11px] text-surface-muted">
                            {e.id} · /duniya/{e.slug}
                          </span>
                          {e.tagline && <span className="text-xs text-surface-muted">{e.tagline}</span>}
                        </div>
                      </td>
                      <td>
                        <div className="flex items-center gap-2">
                          <PaletteSwatches env={env} size={12} />
                          <span className="text-[13px]">{environmentLabel(environments.data, e.environment)}</span>
                        </div>
                        {env && !presetBuilt(env.preset_key) && (
                          <div className="mt-1">
                            <Pill tone="warning" title="The storefront has no viewer preset for this backdrop yet; it renders as the studio">
                              Preset pending
                            </Pill>
                          </div>
                        )}
                      </td>
                      <td className="text-[13px]">{styleLabel(e.style)}</td>
                      <td>
                        {(e.season ?? []).length === 0 ? (
                          <span className="text-xs text-surface-muted">All year</span>
                        ) : (
                          <div className="flex max-w-[260px] flex-wrap gap-1">
                            {(e.season ?? []).map((w, i) => {
                              const now = inSeason(w, today);
                              return (
                                <Pill key={`${w.label}-${i}`} tone={now ? "success" : "neutral"} title={formatWindow(w) + (now ? " · in season today" : "")}>
                                  {now ? "● " : ""}
                                  {w.label}
                                </Pill>
                              );
                            })}
                          </div>
                        )}
                      </td>
                      <td className="num">
                        <span title={avatarsTitle || "No Avatars"}>
                          {e.avatars.length}
                          {families.data && open.length !== e.avatars.length && <span className="block text-[11px] text-surface-muted">{open.length} open</span>}
                        </span>
                      </td>
                      <td className="num">{(e.items ?? []).length}</td>
                      <td>
                        <div className="flex items-center gap-2">
                          <Switch label={`${e.codename} available`} checked={e.available} onChange={(v) => void toggleAvailable(e, v)} disabled={!canWrite} busy={toggling === e.id} />
                          {e.available ? <Pill tone="success">On the Shop</Pill> : <Pill>Coming soon</Pill>}
                        </div>
                      </td>
                      <td className="num">{e.sort_order}</td>
                      <td className="whitespace-nowrap text-xs text-surface-muted">{formatDateTime(e.updated_at)}</td>
                      <td>
                        <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => setEditing(e)} disabled={!lookupsReady}>
                          {canWrite ? "Edit" : "View"}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <ExperienceDrawer
        experience={editing}
        experiences={experiences.data ?? []}
        environments={environments.data ?? []}
        families={families.data ?? []}
        catalog={catalog.data ?? []}
        motifs={motifs.data}
        motifsProblem={motifs.problem}
        onClose={() => setEditing(undefined)}
        onSaved={saved}
      />
    </>
  );
}
