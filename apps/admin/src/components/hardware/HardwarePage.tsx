"use client";

import { useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { AdminHardware, Problem } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { useQuery } from "@/lib/useQuery";
import { useCanWrite } from "@/store/session";
import { HardwareDrawer, toInput } from "./HardwareDrawer";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { OwnerOnlyHint } from "@/components/ui/OwnerOnly";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";
import { Switch } from "@/components/ui/Switch";

export function HardwarePage() {
  const canWrite = useCanWrite();
  const { data, problem, reload, setData } = useQuery(() => api.hardware.list(), "hardware");
  const families = useQuery(() => api.families.list(), "families");
  const [editing, setEditing] = useState<AdminHardware | null | undefined>(undefined);
  const [toggling, setToggling] = useState<string>();
  const [writeProblem, setWriteProblem] = useState<Problem>();

  function saved(h: AdminHardware) {
    setData((prev) => {
      const list = prev ?? [];
      return list.some((x) => x.sku === h.sku) ? list.map((x) => (x.sku === h.sku ? h : x)) : [...list, h];
    });
    setEditing(undefined);
  }

  async function toggleAvailable(h: AdminHardware, available: boolean) {
    setToggling(h.sku);
    setWriteProblem(undefined);
    try {
      saved(await api.hardware.update(h.sku, { ...toInput(h), available }));
    } catch (err) {
      setWriteProblem(toProblem(err));
    } finally {
      setToggling(undefined);
    }
  }

  const usedBy = (sku: string) => families.data?.filter((f) => f.hardware?.some((h) => h.sku === sku)) ?? [];

  return (
    <>
      <PageHeader
        eyebrow="Configuration"
        title="Hardware"
        description="Bought-in parts packed with a piece: split rings, magnets, cords, LED bases. Unit costs feed the price breakdown's hardware line (with the policy's markup) and the packing list in the print pack."
        actions={
          <button type="button" className="ak-btn ak-btn-primary ak-btn-sm" onClick={() => setEditing(null)} disabled={!canWrite} title={canWrite ? undefined : "Owner only"}>
            Add hardware
          </button>
        }
      />
      <OwnerOnlyHint what="Hardware edits" className="mb-4" />
      {writeProblem && <ProblemCard compact problem={writeProblem} className="mb-4" title={writeProblem.code === "forbidden" ? "Owner only" : undefined} />}
      {problem && !data ? (
        <ProblemCard problem={problem} action={{ label: "Try again", onClick: () => void reload() }} />
      ) : !data ? (
        <Loading />
      ) : data.length === 0 ? (
        <EmptyState title="No hardware yet">Add the parts your Avatars need; each one can then be picked in an Avatar&apos;s bill of materials.</EmptyState>
      ) : (
        <div className="ak-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="ak-table">
              <thead>
                <tr>
                  <th>Part</th>
                  <th className="num">Unit cost</th>
                  <th className="num">Weight</th>
                  <th>Supplier</th>
                  <th>Used by</th>
                  <th>Available</th>
                  <th>Updated</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {data.map((h) => {
                  const users = usedBy(h.sku);
                  return (
                    <tr key={h.sku} className={h.available ? "" : "opacity-60"}>
                      <td>
                        <div className="grid gap-0.5">
                          <span className="font-medium">{h.name}</span>
                          <span className="font-mono text-[11px] text-surface-muted">{h.sku}</span>
                          {h.notes && <span className="max-w-xs text-xs text-surface-muted">{h.notes}</span>}
                        </div>
                      </td>
                      <td className="num">{formatPaise(h.unit_cost_paise)}</td>
                      <td className="num">{h.weight_g === undefined ? "—" : `${h.weight_g} g`}</td>
                      <td className="text-surface-muted">
                        {h.url ? (
                          <a href={h.url} target="_blank" rel="noreferrer" className="text-surface-accent hover:underline">
                            {h.supplier || "Link"}
                          </a>
                        ) : (
                          h.supplier || "—"
                        )}
                      </td>
                      <td>
                        {families.data ? (
                          users.length === 0 ? (
                            <span className="text-xs text-surface-muted">No Avatar</span>
                          ) : (
                            <span className="text-xs" title={users.map((f) => f.id).join(", ")}>
                              {users.map((f) => f.codename).join(", ")}
                            </span>
                          )
                        ) : (
                          <span className="text-xs text-surface-muted">…</span>
                        )}
                      </td>
                      <td>
                        <div className="flex items-center gap-2">
                          <Switch label={`${h.name} available`} checked={h.available} onChange={(v) => void toggleAvailable(h, v)} disabled={!canWrite} busy={toggling === h.sku} />
                          {h.available ? <Pill tone="success">In stock</Pill> : <Pill tone="warning">Out of stock</Pill>}
                        </div>
                      </td>
                      <td className="whitespace-nowrap text-xs text-surface-muted">{formatDateTime(h.updated_at)}</td>
                      <td>
                        <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => setEditing(h)}>
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
      <HardwareDrawer item={editing} onClose={() => setEditing(undefined)} onSaved={saved} />
    </>
  );
}
