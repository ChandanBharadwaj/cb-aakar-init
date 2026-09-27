import type { OrderStage } from "@/lib/api/types";
import { ORDER_STAGES, STAGE_LABEL, stageIndex } from "@/lib/orders";

export interface OrderStageTimelineProps {
  stage: OrderStage;
  /** True while the SSE stream is connected. */
  live?: boolean;
  className?: string;
}

/**
 * Board 06: Queued · Slicing · Printing · Sanding · Shipped (· Delivered) as dots on a line,
 * the current stage ringed in the accent with a LIVE badge while streaming.
 */
export function OrderStageTimeline({ stage, live, className }: OrderStageTimelineProps) {
  const cur = stageIndex(stage);
  const cancelled = stage === "cancelled";
  return (
    <ol className={["flex items-start gap-0 text-[11px] font-semibold sm:text-xs", className].filter(Boolean).join(" ")} aria-label="Order progress">
      {ORDER_STAGES.map((s, i) => {
        const done = cur > i;
        const active = cur === i;
        const dot = cancelled ? "bg-danger/60" : done ? "bg-surface-accent" : active ? "bg-surface-accent" : "bg-surface-border";
        return (
          <li key={s} className="flex flex-1 items-start last:flex-none" aria-current={active ? "step" : undefined}>
            <div className="relative grid justify-items-center gap-2">
              <span className={`h-3 w-3 rounded-full ${dot} ${active ? "ak-ring-accent animate-pulse-soft" : ""}`} aria-hidden="true" />
              <span className={done || active ? "text-surface-text" : "text-surface-muted"}>{STAGE_LABEL[s]}</span>
              {active && live && (
                <span className="absolute -top-6 rounded-pill bg-danger px-1.5 py-0.5 text-[9px] font-bold tracking-[0.18em] text-cream" aria-label="Live updates">
                  LIVE
                </span>
              )}
            </div>
            {i < ORDER_STAGES.length - 1 && <span className={`mx-1.5 mt-[5px] h-px flex-1 sm:mx-2.5 ${done ? "bg-surface-accent" : "bg-surface-border"}`} aria-hidden="true" />}
          </li>
        );
      })}
    </ol>
  );
}
