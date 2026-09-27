"use client";

import { useState } from "react";
import { api, toProblem } from "@/lib/api/client";
import type { AdminFamily, Problem } from "@/lib/api/types";
import { shelfLabel } from "@/lib/catalog";
import { FEATURE_TYPES, KIND_TONE, TIER_TONE, familyInput, featureInfo, sortFamilies } from "@/lib/families";
import { formatDateTime } from "@/lib/format";
import { useQuery } from "@/lib/useQuery";
import { useCanWrite } from "@/store/session";
import { AvatarDrawer } from "./AvatarDrawer";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";
import { Switch } from "@/components/ui/Switch";

export function AvatarsPage() {
  const canWrite = useCanWrite();
  const families = useQuery(() => api.families.list(), "families");
  const shelves = useQuery(() => api.catalog.shelves(), "shelves");
  const hardware = useQuery(() => api.hardware.list(), "hardware");
  const materials = useQuery(() => api.materials.list(), "materials");
  const templates = useQuery(() => api.templates.list(), "templates");
  const [editing, setEditing] = useState<AdminFamily | null | undefined>(undefined);
  const [toggling, setToggling] = useState<string>();
  const [problem, setProblem] = useState<Problem>();

  function saved(f: AdminFamily) {
    families.setData((prev) => {
      const list = prev ?? [];
      return sortFamilies(list.some((x) => x.id === f.id) ? list.map((x) => (x.id === f.id ? f : x)) : [...list, f]);
    });
    setEditing(undefined);
  }

  async function toggleAvailable(f: AdminFamily, available: boolean) {
    setToggling(f.id);
    setProblem(undefined);
    try {
      saved(await api.families.update(f.id, { ...familyInput(f), available }));
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setToggling(undefined);
    }
  }

  const rows = families.data ? sortFamilies(families.data) : undefined;

  return (
    <>
      <PageHeader
        eyebrow="Configuration"
        title="Avatars"
        description="Outcome families: the form a customer's idea takes (Saathi · Keychain, Chumbak · Fridge magnet, Swaroop · Print as it is). Codename and copy are brand data you edit here; ids never change. Available shows or hides an Avatar in the Create picker; Ready says whether a live template of the family exists."
        actions={
          <button type="button" className="ak-btn ak-btn-primary ak-btn-sm" onClick={() => setEditing(null)} disabled={!canWrite || !shelves.data} title={canWrite ? undefined : "Owner only"}>
            New Avatar
          </button>
        }
      />
      <OwnerOnlyHint what="Avatar edits" className="mb-4" />
      {problem && <ProblemCard compact problem={problem} className="mb-4" title={problem.code === "forbidden" ? "Owner only" : undefined} />}
      {families.problem && !families.data ? (
        <ProblemCard problem={families.problem} action={{ label: "Try again", onClick: () => void families.reload() }} />
      ) : !rows ? (
        <Loading />
      ) : rows.length === 0 ? (
        <EmptyState title="No Avatars yet">The seed in packages/design-tokens/families.json has not been loaded, or every family was removed. Create one to start.</EmptyState>
      ) : (
        <div className="ak-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="ak-table">
              <thead>
                <tr>
                  <th>Avatar</th>
                  <th>Kind</th>
                  <th>Tier</th>
                  <th>Shelf</th>
                  <th>Chhaap</th>
                  <th>Ready</th>
                  <th>Available</th>
                  <th className="num">Sort</th>
                  <th>Updated</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((f) => {
                  const templateIds = f.template_ids ?? [];
                  const readyTitle = templateIds.length ? `Templates: ${templateIds.join(", ")}` : `No template of family ${f.id} is live in the geometry service`;
                  const accepts = FEATURE_TYPES.filter((t) => f.content_slot.accepts.includes(t.id));
                  return (
                    <tr key={f.id} className={f.available ? "" : "opacity-60"}>
                      <td>
                        <div className="grid gap-0.5">
                          <span className="font-medium">
                            {f.codename} <span className="text-surface-muted">·</span> {f.name}
                          </span>
                          <span className="font-mono text-[11px] text-surface-muted">
                            {f.id} · {f.default_template_id}
                          </span>
                          {f.tagline && <span className="text-xs text-surface-muted">{f.tagline}</span>}
                        </div>
                      </td>
                      <td>
                        <Pill tone={KIND_TONE[f.kind]}>{f.kind}</Pill>
                      </td>
                      <td>
                        <Pill tone={TIER_TONE[f.tier]}>{f.tier}</Pill>
                      </td>
                      <td>{shelfLabel(shelves.data, f.shelf)}</td>
                      <td>
                        {accepts.length === 0 ? (
                          <span className="text-xs text-surface-muted">None</span>
                        ) : (
                          <span className="text-xs" title={f.content_slot.accepts.map((a) => `${featureInfo(a).codename} = ${a}`).join(" · ")}>
                            {accepts.map((a) => a.codename).join(" · ")}
                          </span>
                        )}
                      </td>
                      <td>
                        <Pill tone={f.ready ? "success" : "warning"} title={readyTitle}>
                          {f.ready ? "Ready" : "No template"}
                        </Pill>
                      </td>
                      <td>
                        <div className="flex items-center gap-2">
                          <Switch label={`${f.codename} available`} checked={f.available} onChange={(v) => void toggleAvailable(f, v)} disabled={!canWrite} busy={toggling === f.id} />
                          {f.available ? <Pill tone="success">In picker</Pill> : <Pill>Hidden</Pill>}
                        </div>
                      </td>
                      <td className="num">{f.sort_order}</td>
                      <td className="whitespace-nowrap text-xs text-surface-muted">{formatDateTime(f.updated_at)}</td>
                      <td>
                        <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => setEditing(f)} disabled={!shelves.data}>
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
      <AvatarDrawer family={editing} shelves={shelves.data ?? []} hardware={hardware.data ?? []} materials={materials.data ?? []} templates={templates.data ?? []} onClose={() => setEditing(undefined)} onSaved={saved} />
    </>
  );
}
