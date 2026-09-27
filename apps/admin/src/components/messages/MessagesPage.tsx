"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api/client";
import type { NotificationRecord } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { IS_LOCAL } from "@/lib/profile";
import { useQuery } from "@/lib/useQuery";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loading } from "@/components/ui/Loading";
import { MockNotice } from "@/components/ui/MockNotice";
import { PageHeader } from "@/components/ui/PageHeader";
import { Pagination } from "@/components/ui/Pagination";
import { ProblemCard } from "@/components/ui/ProblemCard";
import { Pill } from "@/components/ui/StatusPill";

const SIZE = 50;
const CHANNEL_TONE: Record<NotificationRecord["channel"], "success" | "info" | "neutral"> = { whatsapp: "success", email: "info", sms: "neutral" };
const STATUS_TONE: Record<NotificationRecord["status"], "neutral" | "success" | "danger"> = { logged: "neutral", sent: "success", failed: "danger" };

export function MessagesPage() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const orderId = params.get("order_id") ?? "";
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0);
  const { data, problem, loading, reload } = useQuery(() => api.notifications.list({ order_id: orderId || undefined, page, size: SIZE }), `notifications:${orderId}:${page}`);
  const [expanded, setExpanded] = useState<string>();

  function go(patch: { page?: number; order_id?: string }) {
    const next = new URLSearchParams(params.toString());
    if (patch.page !== undefined) {
      if (patch.page === 0) next.delete("page");
      else next.set("page", String(patch.page));
    }
    if (patch.order_id !== undefined) {
      if (patch.order_id) next.set("order_id", patch.order_id);
      else next.delete("order_id");
    }
    const qs = next.toString();
    router.push(qs ? `${pathname}?${qs}` : pathname);
  }

  return (
    <>
      <PageHeader
        eyebrow="Customer messages"
        title="Messages"
        description="Everything the notification module produced: WhatsApp, email and SMS, with the rendered text and the order it belongs to."
        actions={<button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" onClick={() => void reload()} disabled={loading}>Refresh</button>}
      />
      {IS_LOCAL && (
        <MockNotice className="mb-4">
          In the local profile the message sender is a mock (ADR-0013): nothing reaches WhatsApp, an inbox or a phone. Each message is recorded here with status <strong>logged</strong> exactly as the real adapter would have sent it. Real adapters record <strong>sent</strong> or <strong>failed</strong>.
        </MockNotice>
      )}
      {orderId && (
        <p className="mb-4 flex flex-wrap items-center gap-2 text-sm">
          <span className="text-surface-muted">Filtered to one order.</span>
          <Link href={`/orders/${orderId}`} className="font-medium text-surface-accent hover:underline">
            Open the order
          </Link>
          <button type="button" className="ak-btn ak-btn-secondary ak-btn-sm ak-btn-pill" onClick={() => go({ order_id: "", page: 0 })}>
            Show all
          </button>
        </p>
      )}
      {problem && !data ? (
        <ProblemCard problem={problem} action={{ label: "Try again", onClick: () => void reload() }} />
      ) : !data ? (
        <Loading />
      ) : data.items.length === 0 ? (
        <EmptyState title="No messages yet">Messages appear when an order is confirmed, printing, shipped or delivered, or when a customer requests a sign-in code.</EmptyState>
      ) : (
        <div className="ak-card overflow-hidden" aria-busy={loading}>
          <div className="overflow-x-auto">
            <table className="ak-table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Channel</th>
                  <th>Template</th>
                  <th>To</th>
                  <th>Status</th>
                  <th>Text</th>
                  <th>Order</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((n) => {
                  const open = expanded === n.id;
                  const text = n.rendered_text ?? "";
                  const long = text.length > 90;
                  return (
                    <tr key={n.id}>
                      <td className="whitespace-nowrap text-xs text-surface-muted">{formatDateTime(n.created_at)}</td>
                      <td>
                        <Pill tone={CHANNEL_TONE[n.channel]}>{n.channel}</Pill>
                      </td>
                      <td className="font-mono text-[12px]">{n.template}</td>
                      <td className="whitespace-nowrap font-mono text-[12px]">{n.to}</td>
                      <td>
                        <Pill tone={STATUS_TONE[n.status]}>{n.status}</Pill>
                      </td>
                      <td className="max-w-md">
                        {text ? (
                          <>
                            <span className="text-[13px]">{open || !long ? text : `${text.slice(0, 90)}…`}</span>
                            {long && (
                              <button type="button" className="ml-1.5 text-xs font-medium text-surface-accent hover:underline" onClick={() => setExpanded(open ? undefined : n.id)}>
                                {open ? "less" : "more"}
                              </button>
                            )}
                          </>
                        ) : (
                          <span className="text-xs text-surface-muted">No rendered text</span>
                        )}
                      </td>
                      <td>
                        {n.order_id ? (
                          <Link href={`/orders/${n.order_id}`} className="font-mono text-[12px] font-semibold text-surface-accent hover:underline">
                            {(n.payload?.order_number as string | undefined) ?? "Open"}
                          </Link>
                        ) : (
                          <span className="text-xs text-surface-muted">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} size={data.size} total={data.total} onPage={(p) => go({ page: p })} className="border-t border-surface-border px-4 py-3" />
        </div>
      )}
    </>
  );
}
