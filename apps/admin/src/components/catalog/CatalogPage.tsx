"use client";

import { useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { CatalogItem, Problem } from "@/lib/api/types";
import { shelfLabel } from "@/lib/catalog";
import { familyTitle } from "@/lib/families";
import { useQuery } from "@/lib/useQuery";
import { useCanWrite } from "@/store/session";
import { CatalogDrawer, toInput } from "./CatalogDrawer";
import { Loading } from "@/components/ui/Loading";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";
import { Switch } from "@/components/ui/Switch";

export function CatalogPage() {
  const canWrite = useCanWrite();
  const items = useQuery(() => api.catalog.list(), "catalog");
  const materials = useQuery(() => api.materials.list(), "materials");
  const templates = useQuery(() => api.templates.list(), "templates");
  const shelves = useQuery(() => api.catalog.shelves(), "shelves");
  const families = useQuery(() => api.families.list(), "families");
  const environments = useQuery(() => api.environments.list(), "environments");
  const [editing, setEditing] = useState<CatalogItem | null | undefined>(undefined);
  const [toggling, setToggling] = useState<string>();
  const [problem, setProblem] = useState<Problem>();

  function saved(item: CatalogItem) {
    items.setData((prev) => {
      const list = prev ?? [];
      return list.some((x) => x.slug === item.slug) ? list.map((x) => (x.slug === item.slug ? item : x)) : [...list, item];
    });
    setEditing(undefined);
  }

  async function toggleAvailable(item: CatalogItem, available: boolean) {
    setToggling(item.slug);
    setProblem(undefined);
    try {
      saved(await api.catalog.update(item.slug, { ...toInput(item), available }));
    } catch (err) {
      setProblem(toProblem(err));
    } finally {
      setToggling(undefined);
    }
  }

  const templateName = (id: string) => templates.data?.find((t) => t.id === id);
  const materialName = (id: string) => materials.data?.find((m) => m.id === id)?.name ?? id;
  const familyOf = (id: string | null | undefined) => (id ? families.data?.find((f) => f.id === id) : undefined);

  return (
    <>
      <PageHeader
        eyebrow="Configuration"
        title="Catalog"
        description="Shop items: the shelf each sits on, the Avatar (outcome family) it belongs to, which template and default parameters it starts from, its default finish, base price and whether it is on sale. Items whose template isn't in the geometry service yet show as 'coming soon'."
        actions={
          <button type="button" className="ak-btn ak-btn-primary ak-btn-sm" onClick={() => setEditing(null)} disabled={!canWrite || !materials.data || !shelves.data} title={canWrite ? undefined : "Owner only"}>
            Add item
          </button>
        }
      />
      <OwnerOnlyHint what="Catalog edits" className="mb-4" />
      {problem && <ProblemCard compact problem={problem} className="mb-4" />}
      {items.problem && !items.data ? (
        <ProblemCard problem={items.problem} action={{ label: "Try again", onClick: () => void items.reload() }} />
      ) : !items.data ? (
        <Loading />
      ) : (
        <div className="ak-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="ak-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Shelf</th>
                  <th>Avatar</th>
                  <th>Template</th>
                  <th>Default material</th>
                  <th className="num">Base price</th>
                  <th>Available</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {items.data.map((it) => {
                  const t = templateName(it.template_id);
                  return (
                    <tr key={it.slug}>
                      <td>
                        <div className="grid gap-0.5">
                          <span className="font-medium">{it.name}</span>
                          <span className="font-mono text-[11px] text-surface-muted">{it.slug}</span>
                          <span className="text-xs text-surface-muted">{it.specs_line}</span>
                        </div>
                      </td>
                      <td>{shelfLabel(shelves.data, it.category)}</td>
                      <td>
                        {it.family_id ? (
                          (() => {
                            const f = familyOf(it.family_id);
                            return (
                              <span className="text-[13px]" title={it.family_id}>
                                {f ? familyTitle(f) : it.family_id}
                              </span>
                            );
                          })()
                        ) : (
                          <span className="text-xs text-surface-muted">—</span>
                        )}
                      </td>
                      <td>
                        <div className="grid gap-0.5">
                          <span className="font-mono text-[12px]">{it.template_id}</span>
                          {templates.data && (t ? <span className="text-[11px] text-surface-muted">{t.live ? "live" : "not live"} · v{t.version}</span> : <span className="text-[11px] text-warning">not in the geometry service</span>)}
                        </div>
                      </td>
                      <td>{materialName(it.default_material)}</td>
                      <td className="num">{formatPaise(it.base_price_paise)}</td>
                      <td>
                        <div className="flex items-center gap-2">
                          <Switch label={`${it.name} available`} checked={it.available} onChange={(v) => void toggleAvailable(it, v)} disabled={!canWrite} busy={toggling === it.slug} />
                          {it.available ? <Pill tone="success">On sale</Pill> : <Pill>Coming soon</Pill>}
                        </div>
                      </td>
                      <td>
                        <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => setEditing(it)} disabled={!materials.data || !shelves.data}>
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
      <CatalogDrawer
        item={editing}
        materials={materials.data ?? []}
        templates={templates.data ?? []}
        shelves={shelves.data ?? []}
        families={families.data ?? []}
        environments={environments.data ?? []}
        onClose={() => setEditing(undefined)}
        onSaved={saved}
      />
    </>
  );
}
