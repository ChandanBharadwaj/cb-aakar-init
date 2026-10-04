"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api/client";
import type { AuditEntry } from "@/lib/api/types";
import { diffEntries } from "@/lib/diff";
import { formatDateTime } from "@/lib/format";
import { useQuery } from "@/lib/useQuery";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { PageHeader } from "@/components/ui/PageHeader";
import { Pagination } from "@/components/ui/Pagination";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";

const SIZE = 50;

function targetHref(entry: AuditEntry): string | undefined {
  const [area] = entry.action.split(".");
  if (area === "order") return `/orders?q=${encodeURIComponent(entry.target)}`;
  if (area === "pricing") return "/pricing";
  if (area === "material") return "/materials";
  if (area === "catalog") return "/catalog";
  if (area === "template") return "/templates";
  if (area === "family") return "/avatars";
  if (area === "hardware") return "/hardware";
  if (area === "review") return "/reviews";
  if (area === "content_term") return "/content-rules";
  return undefined;
}

const AREA_TONE: Record<string, "accent" | "info" | "neutral" | "success" | "warning"> = { order: "accent", pricing: "info", material: "neutral", catalog: "neutral", template: "success", family: "accent", hardware: "neutral", review: "warning", content_term: "warning" };

export function AuditPage() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0);
  const { data, problem, loading, reload } = useQuery(() => api.audit.list({ page, size: SIZE }), `audit:${page}`);
  const [open, setOpen] = useState<Set<number>>(new Set());

  function toggle(id: number) {
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  return (
    <>
      <PageHeader
        eyebrow="Trail"
        title="Audit"
        description="Every write through the management API: who did it, when, what it targeted, and the value before and after."
        actions={<button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" onClick={() => void reload()} disabled={loading}>Refresh</button>}
      />
      {problem && !data ? (
        <ProblemCard problem={problem} action={{ label: "Try again", onClick: () => void reload() }} />
      ) : !data ? (
        <Loading />
      ) : data.items.length === 0 ? (
        <EmptyState title="Nothing recorded yet">The first change made through the portal will appear here.</EmptyState>
      ) : (
        <div className="ak-card overflow-hidden" aria-busy={loading}>
          <div className="overflow-x-auto">
            <table className="ak-table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Who</th>
                  <th>Action</th>
                  <th>Target</th>
                  <th>Change</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((e) => {
                  const area = e.action.split(".")[0] ?? "";
                  const href = targetHref(e);
                  const expanded = open.has(e.id);
                  const rows = diffEntries(e.before, e.after);
                  const changed = rows.filter((r) => r.changed);
                  return [
                    <tr key={e.id}>
                      <td className="whitespace-nowrap text-xs text-surface-muted">
                        {formatDateTime(e.at)}
                        <span className="block font-mono text-[10px] opacity-70">#{e.id}</span>
                      </td>
                      <td className="font-mono text-[12px]">{e.staff_email}</td>
                      <td>
                        <Pill tone={AREA_TONE[area] ?? "neutral"}>{e.action}</Pill>
                      </td>
                      <td>
                        {href ? (
                          <Link href={href} className="font-mono text-[12.5px] font-semibold text-surface-accent hover:underline">
                            {e.target}
                          </Link>
                        ) : (
                          <span className="font-mono text-[12.5px]">{e.target}</span>
                        )}
                      </td>
                      <td>
                        <div className="flex flex-wrap items-center gap-2 text-xs">
                          <span className="text-surface-muted">
                            {e.before === null || e.before === undefined ? "created" : e.after === null || e.after === undefined ? "removed" : `${changed.length} field${changed.length === 1 ? "" : "s"} changed`}
                          </span>
                          {rows.length > 0 && (
                            <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" aria-expanded={expanded} onClick={() => toggle(e.id)}>
                              {expanded ? "Hide diff" : "Show diff"}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>,
                    expanded ? (
                      <tr key={`${e.id}-diff`}>
                        <td colSpan={5} className="bg-surface-bg/60">
                          <table className="w-full text-[12px]">
                            <thead>
                              <tr className="text-left text-[10px] uppercase tracking-wider text-surface-muted">
                                <th className="py-1 pr-3 font-semibold">Field</th>
                                <th className="py-1 pr-3 font-semibold">Before</th>
                                <th className="py-1 font-semibold">After</th>
                              </tr>
                            </thead>
                            <tbody>
                              {rows.map((r) => (
                                <tr key={r.key} className={r.changed ? "" : "opacity-60"}>
                                  <td className="py-1 pr-3 align-top font-mono">{r.key}</td>
                                  <td className={`ak-diff py-1 pr-3 align-top ${r.changed ? "text-danger" : ""}`}>{r.before ?? "—"}</td>
                                  <td className={`ak-diff py-1 align-top ${r.changed ? "text-success" : ""}`}>{r.after ?? "—"}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </td>
                      </tr>
                    ) : null,
                  ];
                })}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            size={data.size}
            total={data.total}
            onPage={(p) => {
              const next = new URLSearchParams(params.toString());
              if (p === 0) next.delete("page");
              else next.set("page", String(p));
              const qs = next.toString();
              router.push(qs ? `${pathname}?${qs}` : pathname);
            }}
            className="border-t border-surface-border px-4 py-3"
          />
        </div>
      )}
    </>
  );
}
