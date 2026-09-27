"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { formatPaise } from "@aakar/design-tokens";
import { api, toProblem } from "@/lib/api/client";
import type { AdminOrder, AdminOrderSummary, OrderStatus, Problem } from "@/lib/api/types";
import { formatDate, formatDateTime, formatRelative } from "@/lib/format";
import { STAGE_LABEL, STATUS_GROUPS, actionLabel, isDisruptive } from "@/lib/orders";
import { useQuery } from "@/lib/useQuery";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { PageHeader } from "@/components/ui/PageHeader";
import { Pagination } from "@/components/ui/Pagination";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill, StatusPill } from "@/components/ui/StatusPill";

const SIZE = 25;

function toSummary(o: AdminOrder): AdminOrderSummary {
  return {
    id: o.id,
    number: o.number,
    status: o.status,
    stage: o.stage,
    title: o.title,
    total_paise: o.total_paise,
    items_count: o.items_count,
    eta: o.eta,
    placed_at: o.placed_at,
    customer: { id: o.customer.id, phone: o.customer.phone, name: o.customer.name },
    materials: [...new Set(o.items.map((i) => i.material_name ?? i.material_id))],
    next_actions: o.next_actions,
  };
}

/** The queue: status-group pills, search by number or phone, a paginated table with inline next actions. */
export function OrderQueue() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const statusParam = params.get("status") ?? "";
  const q = params.get("q") ?? "";
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0);
  const [search, setSearch] = useState(q);
  const [notice, setNotice] = useState<{ problem: Problem; number: string }>();
  const [busy, setBusy] = useState<string>();
  const [confirm, setConfirm] = useState<string>();

  useEffect(() => setSearch(q), [q]);

  const statuses = useMemo(() => statusParam.split(",").map((s) => s.trim()).filter(Boolean), [statusParam]);
  const activeGroup = STATUS_GROUPS.find((g) => (g.id === "all" ? statuses.length === 0 : g.statuses.length === statuses.length && g.statuses.every((s) => statuses.includes(s))));

  const key = `orders:${statusParam}:${q}:${page}`;
  const { data, problem, loading, reload, setData } = useQuery(() => api.orders.list({ status: statusParam || undefined, q: q || undefined, page, size: SIZE }), key);

  const navigate = useCallback(
    (patch: Record<string, string | number | undefined>) => {
      const next = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        if (v === undefined || v === "" || (k === "page" && v === 0)) next.delete(k);
        else next.set(k, String(v));
      }
      const qs = next.toString();
      router.push(qs ? `${pathname}?${qs}` : pathname);
    },
    [params, pathname, router],
  );

  async function advance(order: AdminOrderSummary, to: OrderStatus) {
    const confirmKey = `${order.id}:${to}`;
    if (isDisruptive(to) && confirm !== confirmKey) {
      setConfirm(confirmKey);
      return;
    }
    setConfirm(undefined);
    setBusy(order.id);
    setNotice(undefined);
    try {
      const updated = await api.orders.advance(order.id, { status: to });
      setData((prev) => (prev ? { ...prev, items: prev.items.map((o) => (o.id === updated.id ? toSummary(updated) : o)) } : prev));
    } catch (err) {
      setNotice({ problem: toProblem(err), number: order.number });
    } finally {
      setBusy(undefined);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Fulfilment"
        title="Orders"
        description="Every order, newest first. Pick a stage group, search by order number or phone, and move orders along from the row or from the detail page."
        actions={<button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" onClick={() => void reload()} disabled={loading}>Refresh</button>}
      />

      <div className="mb-4 grid gap-3">
        <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:flex-wrap sm:px-0" role="group" aria-label="Status groups">
          {STATUS_GROUPS.map((g) => (
            <button key={g.id} type="button" className="ak-chip" aria-pressed={activeGroup?.id === g.id} onClick={() => navigate({ status: g.statuses.join(","), page: 0 })}>
              {g.label}
            </button>
          ))}
          {!activeGroup && statuses.length > 0 && (
            <span className="ak-chip" aria-pressed="true">
              {statuses.join(", ")}
            </span>
          )}
        </div>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            navigate({ q: search.trim(), page: 0 });
          }}
          role="search"
        >
          <input type="search" className="ak-input ak-input-sm max-w-sm" placeholder="Order number or phone" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search orders" />
          <button type="submit" className="ak-btn ak-btn-secondary ak-btn-sm">
            Search
          </button>
          {q && (
            <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm" onClick={() => navigate({ q: undefined, page: 0 })}>
              Clear
            </button>
          )}
        </form>
      </div>

      {notice && (
        <ProblemCard compact problem={notice.problem} title={notice.problem.code === "invalid_transition" ? `${notice.number}: that move isn't allowed` : notice.problem.title} className="mb-4">
          <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill justify-self-start" onClick={() => setNotice(undefined)}>
            Dismiss
          </button>
        </ProblemCard>
      )}

      {problem && !data ? (
        <ProblemCard problem={problem} action={{ label: "Try again", onClick: () => void reload() }} />
      ) : !data ? (
        <Loading />
      ) : data.items.length === 0 ? (
        <EmptyState title="Nothing in this view">{q ? `No order matches “${q}”.` : "No orders in this stage group right now."}</EmptyState>
      ) : (
        <div className="ak-card overflow-hidden" aria-busy={loading}>
          <div className="overflow-x-auto">
            <table className="ak-table">
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Items · materials</th>
                  <th>Stage</th>
                  <th>Placed</th>
                  <th>ETA</th>
                  <th className="num">Total</th>
                  <th>Next action</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((o) => (
                  <tr key={o.id} className={busy === o.id ? "opacity-60" : ""}>
                    <td>
                      <Link href={`/orders/${o.id}`} className="font-mono text-[13px] font-semibold text-surface-text hover:text-surface-accent">
                        {o.number}
                      </Link>
                    </td>
                    <td>
                      <div className="grid">
                        <span className="font-medium">{o.customer.name ?? <span className="text-surface-muted">No name</span>}</span>
                        <span className="font-mono text-[12px] text-surface-muted">{o.customer.phone}</span>
                      </div>
                    </td>
                    <td>
                      <div className="grid gap-1">
                        <span>
                          {o.title} <span className="text-surface-muted">× {o.items_count}</span>
                        </span>
                        <span className="flex flex-wrap gap-1">
                          {(o.materials ?? []).map((m) => (
                            <Pill key={m}>{m.replace(/_/g, " ")}</Pill>
                          ))}
                        </span>
                      </div>
                    </td>
                    <td>
                      <div className="grid gap-1">
                        <StatusPill status={o.status} />
                        <span className="text-[11px] text-surface-muted">Customer sees: {STAGE_LABEL[o.stage]}</span>
                      </div>
                    </td>
                    <td title={formatDateTime(o.placed_at)}>{formatRelative(o.placed_at)}</td>
                    <td>{formatDate(o.eta)}</td>
                    <td className="num">{formatPaise(o.total_paise)}</td>
                    <td>
                      <div className="flex flex-wrap gap-1.5">
                        {(o.next_actions ?? []).length === 0 && <span className="text-xs text-surface-muted">—</span>}
                        {(o.next_actions ?? []).map((to) => {
                          const confirming = confirm === `${o.id}:${to}`;
                          const disruptive = isDisruptive(to);
                          return (
                            <button
                              key={to}
                              type="button"
                              className={`ak-btn ak-btn-sm ${disruptive ? (confirming ? "ak-btn-danger" : "ak-btn-secondary") : "ak-btn-primary"}`}
                              disabled={busy === o.id}
                              onClick={() => void advance(o, to)}
                              onBlur={() => confirming && setConfirm(undefined)}
                            >
                              {confirming ? "Confirm?" : actionLabel(to, o.status)}
                            </button>
                          );
                        })}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} size={data.size} total={data.total} onPage={(p) => navigate({ page: p })} className="border-t border-surface-border px-4 py-3" />
        </div>
      )}
    </>
  );
}
