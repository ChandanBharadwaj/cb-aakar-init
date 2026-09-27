import type { OrderStatus } from "@/lib/api/types";
import { LINEAR_PATH, STATUS_LABEL, timelineIndex } from "@/lib/orders";
import { Pill } from "@/components/ui/StatusPill";

export interface OrderTimelineProps {
  status: OrderStatus;
  /** For on_hold: the status the order will resume to (first entry of next_actions). */
  resumeTo?: OrderStatus;
  className?: string;
}

/** The happy path as dots and a line; interruptions (on hold, reprint, cancelled) sit beside the stage they interrupted. */
export function OrderTimeline({ status, resumeTo, className }: OrderTimelineProps) {
  const interrupted = status === "on_hold" || status === "reprint" || status === "cancelled";
  const anchor = status === "on_hold" ? (resumeTo ?? "queued") : status === "reprint" ? "printing" : status === "cancelled" ? "confirmed" : status;
  const cur = status === "pending_payment" ? -1 : timelineIndex(anchor);
  return (
    <div className={["grid gap-3", className].filter(Boolean).join(" ")}>
      <ol className="flex items-center gap-0 text-[11px] font-semibold" aria-label="Order stages">
        {LINEAR_PATH.map((s, i) => {
          const done = cur > i || status === "delivered";
          const active = cur === i && status !== "delivered";
          const color = status === "cancelled" ? "bg-danger" : done || active ? "bg-surface-accent" : "bg-surface-border";
          return (
            <li key={s} className="flex flex-1 items-center last:flex-none" aria-current={active ? "step" : undefined}>
              <div className="grid justify-items-center gap-1.5">
                <span className={`h-2.5 w-2.5 rounded-full ${color} ${active && !interrupted ? "ak-ring-accent animate-pulse-soft" : ""} ${active && interrupted ? "ring-2 ring-warning ring-offset-2 ring-offset-surface-card" : ""}`} aria-hidden="true" />
                <span className={`hidden sm:block ${done || active ? "text-surface-text" : "text-surface-muted"}`}>{STATUS_LABEL[s]}</span>
              </div>
              {i < LINEAR_PATH.length - 1 && <span className={`mx-1.5 mb-0 h-px flex-1 sm:mb-5 ${done ? "bg-surface-accent" : "bg-surface-border"}`} aria-hidden="true" />}
            </li>
          );
        })}
      </ol>
      {status === "pending_payment" && <p className="text-xs text-surface-muted">Payment hasn&apos;t completed. The order enters the queue when the gateway confirms it.</p>}
      {interrupted && (
        <p className="flex flex-wrap items-center gap-2 text-xs text-surface-muted">
          <Pill tone={status === "cancelled" ? "danger" : "warning"}>{STATUS_LABEL[status]}</Pill>
          {status === "on_hold" && resumeTo ? `Paused at ${STATUS_LABEL[resumeTo].toLowerCase()}; resume returns it there.` : status === "reprint" ? "Failed QC; goes back to printing." : "Stopped before shipping."}
        </p>
      )}
    </div>
  );
}
