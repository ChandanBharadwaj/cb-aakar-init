"use client";

import { useState } from "react";
import { api } from "@/lib/api/client";
import type { AdminMaterial } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { useQuery } from "@/lib/useQuery";
import { useCanWrite } from "@/store/session";
import { MaterialDrawer } from "./MaterialDrawer";
import { Swatch } from "./Swatch";
import { Loading } from "@/components/ui/Loading";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";

export function MaterialsPage() {
  const canWrite = useCanWrite();
  const { data, problem, reload, setData } = useQuery(() => api.materials.list(), "materials");
  const [editing, setEditing] = useState<AdminMaterial | null | undefined>(undefined);

  function saved(m: AdminMaterial) {
    setData((prev) => {
      const list = prev ?? [];
      const next = list.some((x) => x.id === m.id) ? list.map((x) => (x.id === m.id ? m : x)) : [...list, m];
      return [...next].sort((a, b) => a.sort_order - b.sort_order);
    });
    setEditing(undefined);
  }

  return (
    <>
      <PageHeader
        eyebrow="Configuration"
        title="Materials"
        description="Digital materials: the filament each maps to, its density and rate, the finish class that sets the finishing fee, and the PBR preset the storefront viewer renders."
        actions={
          <button type="button" className="ak-btn ak-btn-primary ak-btn-sm" onClick={() => setEditing(null)} disabled={!canWrite} title={canWrite ? undefined : "Owner only"}>
            Add material
          </button>
        }
      />
      <OwnerOnlyHint what="Material edits" className="mb-4" />
      {problem && !data ? (
        <ProblemCard problem={problem} action={{ label: "Try again", onClick: () => void reload() }} />
      ) : !data ? (
        <Loading />
      ) : (
        <div className="ak-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="ak-table">
              <thead>
                <tr>
                  <th>Material</th>
                  <th>Filament</th>
                  <th className="num">Density</th>
                  <th>Finish</th>
                  <th className="num">₹/g</th>
                  <th>Heat safe</th>
                  <th>Available</th>
                  <th className="num">Sort</th>
                  <th>Updated</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {data.map((m) => (
                  <tr key={m.id} className={m.available ? "" : "opacity-60"}>
                    <td>
                      <div className="flex items-center gap-2.5">
                        <Swatch pbr={m.pbr} finishClass={m.finish_class} size={22} />
                        <div className="grid">
                          <span className="font-medium">{m.name}</span>
                          <span className="font-mono text-[11px] text-surface-muted">
                            {m.id} · {m.pbr.color}
                          </span>
                        </div>
                      </div>
                    </td>
                    <td className="text-surface-muted">{m.filament}</td>
                    <td className="num">{m.density_g_cm3.toFixed(2)}</td>
                    <td>
                      <Pill tone={m.finish_class === "silk" ? "accent" : "neutral"}>{m.finish_class}</Pill>
                    </td>
                    <td className="num">{(m.rate_per_g_paise / 100).toFixed(2)}</td>
                    <td>{m.heat_safe ? "Yes" : "No"}</td>
                    <td>{m.available ? <Pill tone="success">Available</Pill> : <Pill tone="warning">Hidden</Pill>}</td>
                    <td className="num">{m.sort_order}</td>
                    <td className="whitespace-nowrap text-xs text-surface-muted">{formatDateTime(m.updated_at)}</td>
                    <td>
                      <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => setEditing(m)}>
                        {canWrite ? "Edit" : "View"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <MaterialDrawer material={editing} onClose={() => setEditing(undefined)} onSaved={saved} />
    </>
  );
}
