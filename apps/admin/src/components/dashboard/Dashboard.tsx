"use client";

import Link from "next/link";
import { formatPaise } from "@aakar/design-tokens";
import { api } from "@/lib/api/client";
import type { OrderStatus } from "@/lib/api/types";
import { STATUS_GROUPS, STATUS_LABEL, STATUS_TONE } from "@/lib/orders";
import { useQuery } from "@/lib/useQuery";
import { Loading } from "@/components/ui/Loading";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";
import { Tile } from "@/components/ui/Tile";

const ORDER: OrderStatus[] = ["pending_payment", "confirmed", "queued", "slicing", "printing", "finishing", "qc", "packed", "shipped", "delivered", "on_hold", "reprint", "cancelled"];
const BAR: Record<string, string> = { neutral: "bg-surface-border", accent: "bg-terracotta", info: "bg-indigo", success: "bg-success", warning: "bg-warning", danger: "bg-danger" };

export function Dashboard() {
  const { data, problem, loading, reload } = useQuery(() => api.dashboard(), "dashboard");
  const counts = data?.orders_by_status ?? {};
  const total = Object.values(counts).reduce((s, n) => s + n, 0);
  const max = Math.max(1, ...Object.values(counts));
  const groupCount = (statuses: OrderStatus[]) => statuses.reduce((s, st) => s + (counts[st] ?? 0), 0);
  const production = STATUS_GROUPS.find((g) => g.id === "production")!;

  return (
    <>
      <PageHeader eyebrow="Studio board" title="Dashboard" description="Today at a glance. Numbers come from the management API; tiles link into the queue." actions={<button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" onClick={() => void reload()} disabled={loading}>Refresh</button>} />
      {problem && !data ? (
        <ProblemCard problem={problem} action={{ label: "Try again", onClick: () => void reload() }} />
      ) : !data ? (
        <Loading />
      ) : (
        <div className="grid gap-6">
          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" aria-label="Today">
            <Tile label="Orders today" value={data.orders_today} hint="Placed since midnight IST" tone="info" href="/orders" />
            <Tile label="Revenue today" value={formatPaise(data.revenue_today_paise)} hint="Paid orders, incl. shipping" tone="success" />
            <Tile label="Revenue this month" value={formatPaise(data.revenue_month_paise)} hint="Paid orders, incl. shipping" tone="success" />
            <Tile label="Awaiting action" value={data.awaiting_action} hint="Queued, finishing or QC: a staff step is due" tone={data.awaiting_action > 0 ? "accent" : "neutral"} href={`/orders?status=${["queued", "finishing", "qc"].join(",")}`} />
          </section>

          <section className="grid gap-4 lg:grid-cols-[1fr_320px]">
            <div className="ak-card grid gap-4 p-5">
              <div className="flex items-baseline justify-between gap-3">
                <h2 className="font-display text-2xl font-semibold">Orders by status</h2>
                <span className="text-xs text-surface-muted">{total} open and closed</span>
              </div>
              <ul className="grid gap-2">
                {ORDER.filter((s) => (counts[s] ?? 0) > 0).map((s) => {
                  const n = counts[s] ?? 0;
                  return (
                    <li key={s}>
                      <Link href={`/orders?status=${s}`} className="grid grid-cols-[150px_1fr_40px] items-center gap-3 rounded-control px-2 py-1.5 text-sm hover:bg-surface-bg">
                        <Pill tone={STATUS_TONE[s]}>{STATUS_LABEL[s]}</Pill>
                        <span className="h-2 overflow-hidden rounded-pill bg-surface-bg" aria-hidden="true">
                          <span className={`block h-full rounded-pill ${BAR[STATUS_TONE[s]]}`} style={{ width: `${Math.max(4, (n / max) * 100)}%` }} />
                        </span>
                        <span className="text-right font-semibold tabular-nums">{n}</span>
                      </Link>
                    </li>
                  );
                })}
                {total === 0 && <li className="text-sm text-surface-muted">No orders yet.</li>}
              </ul>
            </div>

            <div className="grid gap-3">
              <h2 className="ak-label px-1">Queue shortcuts</h2>
              {STATUS_GROUPS.filter((g) => g.statuses.length > 0).map((g) => (
                <Link key={g.id} href={`/orders?status=${g.statuses.join(",")}`} className="ak-card flex items-center justify-between gap-3 px-4 py-3 text-sm transition-all duration-base ease-ak hover:-translate-y-0.5 hover:shadow-float">
                  <span className="font-medium">{g.label}</span>
                  <span className="font-display text-xl font-semibold tabular-nums">{groupCount(g.statuses)}</span>
                </Link>
              ))}
              <p className="px-1 text-[11px] text-surface-muted">In production = {production.statuses.map((s) => STATUS_LABEL[s].toLowerCase()).join(", ")}.</p>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
